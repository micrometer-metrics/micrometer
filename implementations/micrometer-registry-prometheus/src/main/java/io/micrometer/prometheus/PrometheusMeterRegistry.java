/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.CumulativeHistogramLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultGauge;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * {@link MeterRegistry} for Prometheus.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public class PrometheusMeterRegistry extends MeterRegistry {
    private final PrometheusConfig prometheusConfig;
    private final CollectorRegistry registry;
    private final ConcurrentMap<String, MicrometerCollector> collectorMap = new ConcurrentHashMap<>();

    public PrometheusMeterRegistry(PrometheusConfig config) {
        this(config, new CollectorRegistry(), Clock.SYSTEM);
    }

    public PrometheusMeterRegistry(PrometheusConfig config, CollectorRegistry registry, Clock clock) {
        super(clock);

        config.requireValid();

        this.prometheusConfig = config;
        this.registry = registry;

        config().namingConvention(new PrometheusNamingConvention());
        config().onMeterRemoved(this::onMeterRemoved);
    }

    private static List<String> tagValues(Meter.Id id) {
        return stream(id.getTagsAsIterable().spliterator(), false).map(Tag::getValue).collect(toList());
    }

    /**
     * @return Content that should be included in the response body for an endpoint designated for
     * Prometheus to scrape from.
     */
    public String scrape() {
        Writer writer = new StringWriter();
        try {
            scrape(writer);
        } catch (IOException e) {
            // This actually never happens since StringWriter::write() doesn't throw any IOException
            throw new RuntimeException(e);
        }
        return writer.toString();
    }

    /**
     * Scrape to the specified writer.
     *
     * @param writer Target that serves the content to be scraped by Prometheus.
     * @throws IOException if writing fails
     * @since 1.2.0
     */
    public void scrape(Writer writer) throws IOException {
        TextFormat.write004(writer, registry.metricFamilySamples());
    }

    @Override
    public Counter newCounter(Meter.Id id) {
        PrometheusCounter counter = new PrometheusCounter(id);
        applyToCollector(id, (collector) -> {
            List<String> tagValues = tagValues(id);
            collector.add(tagValues, (conventionName, tagKeys) -> Stream.of(new MicrometerCollector.Family(Collector.Type.COUNTER, conventionName,
                    new Collector.MetricFamilySamples.Sample(conventionName, tagKeys, tagValues, counter.count()))));
        });
        return counter;
    }

    @Override
    public DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        PrometheusDistributionSummary summary = new PrometheusDistributionSummary(id, clock, distributionStatisticConfig, scale, prometheusConfig.histogramFlavor());
        applyToCollector(id, (collector) -> {
            List<String> tagValues = tagValues(id);
            collector.add(tagValues, (conventionName, tagKeys) -> {
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
                    String sampleName = conventionName + "_bucket";
                    switch (summary.histogramFlavor()) {
                        case Prometheus:
                            histogramKeys.add("le");

                            // satisfies https://prometheus.io/docs/concepts/metric_types/#histogram
                            for (CountAtBucket c : histogramCounts) {
                                final List<String> histogramValues = new LinkedList<>(tagValues);
                                histogramValues.add(Collector.doubleToGoString(c.bucket()));
                                samples.add(new Collector.MetricFamilySamples.Sample(
                                        sampleName, histogramKeys, histogramValues, c.count()));
                            }

                            if (Double.isFinite(histogramCounts[histogramCounts.length - 1].bucket())) {
                                // the +Inf bucket should always equal `count`
                                final List<String> histogramValues = new LinkedList<>(tagValues);
                                histogramValues.add("+Inf");
                                samples.add(new Collector.MetricFamilySamples.Sample(
                                        sampleName, histogramKeys, histogramValues, count));
                            }
                            break;
                        case VictoriaMetrics:
                            histogramKeys.add("vmrange");

                            for (CountAtBucket c : histogramCounts) {
                                final List<String> histogramValuesVM = new LinkedList<>(tagValues);
                                histogramValuesVM.add(FixedBoundaryVictoriaMetricsHistogram.getRangeTagValue(c.bucket()));
                                samples.add(new Collector.MetricFamilySamples.Sample(
                                        sampleName, histogramKeys, histogramValuesVM, c.count()));
                            }
                            break;
                        default:
                            break;
                    }

                }

                samples.add(new Collector.MetricFamilySamples.Sample(
                        conventionName + "_count", tagKeys, tagValues, count));

                samples.add(new Collector.MetricFamilySamples.Sample(
                        conventionName + "_sum", tagKeys, tagValues, summary.totalAmount()));

                return Stream.of(new MicrometerCollector.Family(type, conventionName, samples.build()),
                        new MicrometerCollector.Family(Collector.Type.GAUGE, conventionName + "_max",
                                new Collector.MetricFamilySamples.Sample(conventionName + "_max", tagKeys, tagValues, summary.max())));
            });
        });
        return summary;
    }

    @Override
    protected io.micrometer.core.instrument.Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        PrometheusTimer timer = new PrometheusTimer(id, clock, distributionStatisticConfig, pauseDetector, prometheusConfig.histogramFlavor());
        applyToCollector(id, (collector) ->
                addDistributionStatisticSamples(distributionStatisticConfig, collector, timer, tagValues(id), false));
        return timer;
    }

    @Override
    protected <T> io.micrometer.core.instrument.Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        Gauge gauge = new DefaultGauge<>(id, obj, valueFunction);
        applyToCollector(id, (collector) -> {
            List<String> tagValues = tagValues(id);
            collector.add(tagValues, (conventionName, tagKeys) -> Stream.of(new MicrometerCollector.Family(Collector.Type.GAUGE, conventionName,
                    new Collector.MetricFamilySamples.Sample(conventionName, tagKeys, tagValues, gauge.value()))));
        });
        return gauge;
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        LongTaskTimer ltt = new CumulativeHistogramLongTaskTimer(id, clock, getBaseTimeUnit(), distributionStatisticConfig);
        applyToCollector(id, (collector) ->
                addDistributionStatisticSamples(distributionStatisticConfig, collector, ltt, tagValues(id), true));
        return ltt;
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        FunctionTimer ft = new CumulativeFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit, getBaseTimeUnit());
        applyToCollector(id, (collector) -> {
            List<String> tagValues = tagValues(id);
            collector.add(tagValues, (conventionName, tagKeys) -> Stream.of(new MicrometerCollector.Family(Collector.Type.SUMMARY, conventionName,
                    new Collector.MetricFamilySamples.Sample(conventionName + "_count", tagKeys, tagValues, ft.count()),
                    new Collector.MetricFamilySamples.Sample(conventionName + "_sum", tagKeys, tagValues, ft.totalTime(TimeUnit.SECONDS))
            )));
        });
        return ft;
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        FunctionCounter fc = new CumulativeFunctionCounter<>(id, obj, countFunction);
        applyToCollector(id, (collector) -> {
            List<String> tagValues = tagValues(id);
            collector.add(tagValues, (conventionName, tagKeys) -> Stream.of(new MicrometerCollector.Family(Collector.Type.COUNTER, conventionName,
                    new Collector.MetricFamilySamples.Sample(conventionName, tagKeys, tagValues, fc.count())
            )));
        });
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

        final Collector.Type finalPromType = promType;

        applyToCollector(id, (collector) -> {
            List<String> tagValues = tagValues(id);
            collector.add(tagValues, (conventionName, tagKeys) -> {
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

    private void addDistributionStatisticSamples(DistributionStatisticConfig distributionStatisticConfig, MicrometerCollector collector,
                                                 HistogramSupport histogramSupport, List<String> tagValues, boolean forLongTaskTimer) {
        collector.add(tagValues, (conventionName, tagKeys) -> {
            Stream.Builder<Collector.MetricFamilySamples.Sample> samples = Stream.builder();

            HistogramSnapshot histogramSnapshot = histogramSupport.takeSnapshot();
            ValueAtPercentile[] percentileValues = histogramSnapshot.percentileValues();
            CountAtBucket[] histogramCounts = histogramSnapshot.histogramCounts();
            double count = histogramSnapshot.count();

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

                String sampleName = conventionName + "_bucket";
                switch (prometheusConfig.histogramFlavor()) {
                    case Prometheus:
                        histogramKeys.add("le");

                        // satisfies https://prometheus.io/docs/concepts/metric_types/#histogram
                        for (CountAtBucket c : histogramCounts) {
                            final List<String> histogramValues = new LinkedList<>(tagValues);
                            histogramValues.add(Collector.doubleToGoString(c.bucket(TimeUnit.SECONDS)));
                            samples.add(new Collector.MetricFamilySamples.Sample(
                                    sampleName, histogramKeys, histogramValues, c.count()));
                        }

                        // the +Inf bucket should always equal `count`
                        final List<String> histogramValues = new LinkedList<>(tagValues);
                        histogramValues.add("+Inf");
                        samples.add(new Collector.MetricFamilySamples.Sample(
                                sampleName, histogramKeys, histogramValues, count));
                        break;
                    case VictoriaMetrics:
                        histogramKeys.add("vmrange");

                        for (CountAtBucket c : histogramCounts) {
                            final List<String> histogramValuesVM = new LinkedList<>(tagValues);
                            histogramValuesVM.add(FixedBoundaryVictoriaMetricsHistogram.getRangeTagValue(c.bucket()));
                            samples.add(new Collector.MetricFamilySamples.Sample(
                                    sampleName, histogramKeys, histogramValuesVM, c.count()));
                        }
                        break;
                    default:
                        break;
                }

            }

            samples.add(new Collector.MetricFamilySamples.Sample(
                    conventionName + (forLongTaskTimer ? "_active_count" : "_count"), tagKeys, tagValues, count));

            samples.add(new Collector.MetricFamilySamples.Sample(
                    conventionName + (forLongTaskTimer ? "_duration_sum" : "_sum"), tagKeys, tagValues, histogramSnapshot.total(TimeUnit.SECONDS)));

            return Stream.of(new MicrometerCollector.Family(type, conventionName, samples.build()),
                    new MicrometerCollector.Family(Collector.Type.GAUGE, conventionName + "_max", Stream.of(
                            new Collector.MetricFamilySamples.Sample(conventionName + "_max", tagKeys, tagValues,
                                    histogramSnapshot.max(getBaseTimeUnit())))));
        });
    }

    private void onMeterRemoved(Meter meter) {
        MicrometerCollector collector = collectorMap.get(getConventionName(meter.getId()));
        if (collector != null) {
            collector.remove(tagValues(meter.getId()));
            if (collector.isEmpty()) {
                collectorMap.remove(getConventionName(meter.getId()));
                getPrometheusRegistry().unregister(collector);
            }
        }
    }

    private void applyToCollector(Meter.Id id, Consumer<MicrometerCollector> consumer) {
        collectorMap.compute(getConventionName(id), (name, existingCollector) -> {
            if (existingCollector == null) {
                MicrometerCollector micrometerCollector = new MicrometerCollector(id, config().namingConvention(), prometheusConfig);
                consumer.accept(micrometerCollector);
                return micrometerCollector.register(registry);
            }

            List<String> tagKeys = getConventionTags(id).stream().map(Tag::getKey).collect(toList());
            if (existingCollector.getTagKeys().equals(tagKeys)) {
                consumer.accept(existingCollector);
                return existingCollector;
            }

            meterRegistrationFailed(id, "Prometheus requires that all meters with the same name have the same" +
                    " set of tag keys. There is already an existing meter named '" + id.getName() + "' containing tag keys [" +
                    String.join(", ", collectorMap.get(getConventionName(id)).getTagKeys()) + "]. The meter you are attempting to register" +
                    " has keys [" + getConventionTags(id).stream().map(Tag::getKey).collect(joining(", ")) + "].");
            return existingCollector;
        });
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.builder()
                .expiry(prometheusConfig.step())
                .build()
                .merge(DistributionStatisticConfig.DEFAULT);
    }

    /**
     * For use with {@link MeterRegistry.Config#onMeterRegistrationFailed(BiConsumer)} when you want meters with the same name
     * but different tags to cause an unchecked exception.
     *
     * @return This registry
     * @since 1.6.0
     */
    public PrometheusMeterRegistry throwExceptionOnRegistrationFailure() {
        config().onMeterRegistrationFailed((id, reason) -> {
            throw new IllegalArgumentException(reason);
        });

        return this;
    }
}
