/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.prometheus;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionCounter;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionTimer;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.lang.Nullable;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * @author Jon Schneider
 */
public class PrometheusMeterRegistry extends MeterRegistry {
    private final CollectorRegistry registry;
    private final ConcurrentMap<String, MicrometerCollector> collectorMap = new ConcurrentHashMap<>();
    private final PrometheusConfig prometheusConfig;

    public PrometheusMeterRegistry(PrometheusConfig config) {
        this(config, new CollectorRegistry(), Clock.SYSTEM);
    }

    public PrometheusMeterRegistry(PrometheusConfig config, CollectorRegistry registry, Clock clock) {
        super(clock);
        this.registry = registry;
        this.config().namingConvention(new PrometheusNamingConvention());
        this.prometheusConfig = config;
    }

    /**
     * @return Content that should be included in the response body for an endpoint designate for
     * Prometheus to scrape from.
     */
    public String scrape() {
        Writer writer = new StringWriter();
        try {
            TextFormat.write004(writer, registry.metricFamilySamples());
        } catch (IOException e) {
            // This actually never happens since StringWriter::write() doesn't throw any IOException
            throw new RuntimeException(e);
        }
        return writer.toString();
    }

    @Override
    public Counter newCounter(Meter.Id id) {
        MicrometerCollector collector = collectorByName(id);
        PrometheusCounter counter = new PrometheusCounter(id);
        List<String> tagValues = tagValues(id);

        collector.add((conventionName, tagKeys) -> Stream.of(new MicrometerCollector.Family(Collector.Type.COUNTER, conventionName,
                Stream.of(new Collector.MetricFamilySamples.Sample(conventionName, tagKeys, tagValues, counter.count())))));

        return counter;
    }

    @Override
    public DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        MicrometerCollector collector = collectorByName(id);
        PrometheusDistributionSummary summary = new PrometheusDistributionSummary(id, clock, distributionStatisticConfig, scale);
        List<String> tagValues = tagValues(id);

        collector.add((conventionName, tagKeys) -> {
            Stream.Builder<Collector.MetricFamilySamples.Sample> samples = Stream.builder();

            final ValueAtPercentile[] percentileValues = summary.takeSnapshot().percentileValues();
            final CountAtBucket[] histogramCounts = summary.histogramCounts();
            double count = summary.count();

            if (percentileValues.length > 0) {
                List<String> quantileKeys = new LinkedList<>(tagKeys);
                quantileKeys.add("quantile");

                // satisfies https://prometheus.io/docs/concepts/metric_types/#summary
                for (ValueAtPercentile v : percentileValues) {
                    List<String> quantileValues = new LinkedList<>(tagValues);
                    quantileValues.add(Collector.doubleToGoString(v.percentile()));
                    samples.add(new Collector.MetricFamilySamples.Sample(
                            conventionName, quantileKeys, quantileValues, v.value()));
                }
            }

            Collector.Type type = Collector.Type.SUMMARY;
            if (histogramCounts.length > 0) {
                // Prometheus doesn't balk at a metric being BOTH a histogram and a summary
                type = Collector.Type.HISTOGRAM;

                List<String> histogramKeys = new LinkedList<>(tagKeys);
                histogramKeys.add("le");

                // satisfies https://prometheus.io/docs/concepts/metric_types/#histogram
                for (CountAtBucket c : histogramCounts) {
                    final List<String> histogramValues = new LinkedList<>(tagValues);
                    histogramValues.add(Collector.doubleToGoString(c.bucket()));
                    samples.add(new Collector.MetricFamilySamples.Sample(
                            conventionName + "_bucket", histogramKeys, histogramValues, c.count()));
                }

                // the +Inf bucket should always equal `count`
                final List<String> histogramValues = new LinkedList<>(tagValues);
                histogramValues.add("+Inf");
                samples.add(new Collector.MetricFamilySamples.Sample(
                        conventionName + "_bucket", histogramKeys, histogramValues, count));
            }

            samples.add(new Collector.MetricFamilySamples.Sample(
                    conventionName + "_count", tagKeys, tagValues, count));

            samples.add(new Collector.MetricFamilySamples.Sample(
                    conventionName + "_sum", tagKeys, tagValues, summary.totalAmount()));

            return Stream.of(new MicrometerCollector.Family(type, conventionName, samples.build()),
                    new MicrometerCollector.Family(Collector.Type.GAUGE, conventionName + "_max", Stream.of(
                            new Collector.MetricFamilySamples.Sample(conventionName + "_max", tagKeys, tagValues, summary.max()))));
        });

        return summary;
    }

    @Override
    protected io.micrometer.core.instrument.Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        MicrometerCollector collector = collectorByName(id);
        PrometheusTimer timer = new PrometheusTimer(id, clock, distributionStatisticConfig, pauseDetector);
        List<String> tagValues = tagValues(id);

        collector.add((conventionName, tagKeys) -> {
            Stream.Builder<Collector.MetricFamilySamples.Sample> samples = Stream.builder();

            final ValueAtPercentile[] percentileValues = timer.takeSnapshot().percentileValues();
            final CountAtBucket[] histogramCounts = timer.histogramCounts();
            double count = timer.count();

            if (percentileValues.length > 0) {
                List<String> quantileKeys = new LinkedList<>(tagKeys);
                quantileKeys.add("quantile");

                // satisfies https://prometheus.io/docs/concepts/metric_types/#summary
                for (ValueAtPercentile v : percentileValues) {
                    List<String> quantileValues = new LinkedList<>(tagValues);
                    quantileValues.add(Collector.doubleToGoString(v.percentile()));
                    samples.add(new Collector.MetricFamilySamples.Sample(
                            conventionName, quantileKeys, quantileValues, v.value(TimeUnit.SECONDS)));
                }
            }

            Collector.Type type = distributionStatisticConfig.isPublishingHistogram() ? Collector.Type.HISTOGRAM : Collector.Type.SUMMARY;
            if (histogramCounts.length > 0) {
                // Prometheus doesn't balk at a metric being BOTH a histogram and a summary
                type = Collector.Type.HISTOGRAM;

                List<String> histogramKeys = new LinkedList<>(tagKeys);
                histogramKeys.add("le");

                // satisfies https://prometheus.io/docs/concepts/metric_types/#histogram
                for (CountAtBucket c : histogramCounts) {
                    final List<String> histogramValues = new LinkedList<>(tagValues);
                    histogramValues.add(Collector.doubleToGoString(c.bucket(TimeUnit.SECONDS)));
                    samples.add(new Collector.MetricFamilySamples.Sample(
                            conventionName + "_bucket", histogramKeys, histogramValues, c.count()));
                }

                // the +Inf bucket should always equal `count`
                final List<String> histogramValues = new LinkedList<>(tagValues);
                histogramValues.add("+Inf");
                samples.add(new Collector.MetricFamilySamples.Sample(
                        conventionName + "_bucket", histogramKeys, histogramValues, count));
            }

            samples.add(new Collector.MetricFamilySamples.Sample(
                    conventionName + "_count", tagKeys, tagValues, count));

            samples.add(new Collector.MetricFamilySamples.Sample(
                    conventionName + "_sum", tagKeys, tagValues, timer.totalTime(TimeUnit.SECONDS)));

            return Stream.of(new MicrometerCollector.Family(type, conventionName, samples.build()),
                    new MicrometerCollector.Family(Collector.Type.GAUGE, conventionName + "_max", Stream.of(
                            new Collector.MetricFamilySamples.Sample(conventionName + "_max", tagKeys, tagValues,
                                    timer.max(getBaseTimeUnit())))));
        });

        return timer;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> io.micrometer.core.instrument.Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        MicrometerCollector collector = collectorByName(id);
        Gauge gauge = new DefaultGauge(id, obj, valueFunction);
        List<String> tagValues = tagValues(id);

        collector.add((conventionName, tagKeys) -> Stream.of(new MicrometerCollector.Family(Collector.Type.GAUGE, conventionName, Stream.of(
                new Collector.MetricFamilySamples.Sample(conventionName, tagKeys, tagValues, gauge.value())
        ))));

        return gauge;
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        MicrometerCollector collector = collectorByName(id);
        LongTaskTimer ltt = new DefaultLongTaskTimer(id, clock);
        List<String> tagValues = tagValues(id);

        collector.add((conventionName, tagKeys) -> Stream.of(new MicrometerCollector.Family(Collector.Type.UNTYPED, conventionName, Stream.of(
                new Collector.MetricFamilySamples.Sample(conventionName + "_active_count", tagKeys, tagValues, ltt.activeTasks()),
                new Collector.MetricFamilySamples.Sample(conventionName + "_duration_sum", tagKeys, tagValues, ltt.duration(TimeUnit.SECONDS))
        ))));

        return ltt;
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        MicrometerCollector collector = collectorByName(id);
        FunctionTimer ft = new CumulativeFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit, getBaseTimeUnit());
        List<String> tagValues = tagValues(id);

        collector.add((conventionName, tagKeys) -> Stream.of(new MicrometerCollector.Family(Collector.Type.SUMMARY, conventionName, Stream.of(
                new Collector.MetricFamilySamples.Sample(conventionName + "_count", tagKeys, tagValues, ft.count()),
                new Collector.MetricFamilySamples.Sample(conventionName + "_sum", tagKeys, tagValues, ft.totalTime(TimeUnit.SECONDS))
        ))));

        return ft;
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        MicrometerCollector collector = collectorByName(id);
        FunctionCounter fc = new CumulativeFunctionCounter<>(id, obj, countFunction);
        List<String> tagValues = tagValues(id);

        collector.add((conventionName, tagKeys) -> Stream.of(new MicrometerCollector.Family(Collector.Type.COUNTER, conventionName, Stream.of(
                new Collector.MetricFamilySamples.Sample(conventionName, tagKeys, tagValues, fc.count())
        ))));

        return fc;
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        Collector.Type promType = Collector.Type.UNTYPED;
        switch (type) {
            case COUNTER:
                promType = Collector.Type.COUNTER;
                break;
            case GAUGE:
                promType = Collector.Type.GAUGE;
                break;
            case DISTRIBUTION_SUMMARY:
            case TIMER:
                promType = Collector.Type.SUMMARY;
                break;
        }

        MicrometerCollector collector = collectorByName(id);
        List<String> tagValues = tagValues(id);

        final Collector.Type finalPromType = promType;
        collector.add((conventionName, tagKeys) -> {
            List<String> statKeys = new LinkedList<>(tagKeys);
            statKeys.add("statistic");

            return Stream.of(new MicrometerCollector.Family(finalPromType, conventionName,
                    stream(measurements.spliterator(), false)
                            .map(m -> {
                                List<String> statValues = new LinkedList<>(tagValues);
                                statValues.add(m.getStatistic().toString());

                                String name = conventionName;
                                switch (m.getStatistic()) {
                                    case TOTAL:
                                    case TOTAL_TIME:
                                        name += "_sum";
                                        break;
                                    case MAX:
                                        name += "_max";
                                        break;
                                    case ACTIVE_TASKS:
                                        name += "_active_count";
                                        break;
                                    case DURATION:
                                        name += "_duration_sum";
                                        break;
                                }

                                return new Collector.MetricFamilySamples.Sample(name, statKeys, statValues, m.getValue());
                            })));
        });

        return new DefaultMeter(id, type, measurements);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }

    /**
     * @return The underlying Prometheus {@link CollectorRegistry}.
     */
    public CollectorRegistry getPrometheusRegistry() {
        return registry;
    }

    private static List<String> tagValues(Meter.Id id) {
        return id.getTags().stream().map(Tag::getValue).collect(toList());
    }

    private MicrometerCollector collectorByName(Meter.Id id) {
        return collectorMap.compute(getConventionName(id), (name, existingCollector) -> {
            if (existingCollector == null) {
                return new MicrometerCollector(id, config().namingConvention(), prometheusConfig).register(registry);
            }

            List<String> tagKeys = getConventionTags(id).stream().map(Tag::getKey).collect(toList());
            if (existingCollector.getTagKeys().equals(tagKeys)) {
                return existingCollector;
            }

            throw new IllegalArgumentException("Prometheus requires that all meters with the same name have the same" +
                    " set of tag keys. There is already an existing meter containing tag keys [" +
                    existingCollector.getTagKeys().stream().collect(joining(", ")) + "]. The meter you are attempting to register" +
                    " has keys [" + tagKeys.stream().collect(joining(", ")) + "].");
        });
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.builder()
                .expiry(prometheusConfig.step())
                .build()
                .merge(DistributionStatisticConfig.DEFAULT);
    }
}
