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
import io.micrometer.core.instrument.internal.DefaultFunctionTimer;
import io.micrometer.core.instrument.stats.hist.BucketFilter;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.hist.PercentileTimeHistogram;
import io.micrometer.core.instrument.stats.hist.TimeHistogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.prometheus.internal.*;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.SimpleCollector;
import io.prometheus.client.exporter.common.TextFormat;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * @author Jon Schneider
 */
public class PrometheusMeterRegistry extends AbstractMeterRegistry {
    private final CollectorRegistry registry;
    private final ConcurrentMap<String, Collector> collectorMap = new ConcurrentHashMap<>();
    private final boolean sendDescriptions;
    private final PrometheusConfig prometheusConfig;

    public PrometheusMeterRegistry(PrometheusConfig config) {
        this(config, new CollectorRegistry(), Clock.SYSTEM);
    }

    public PrometheusMeterRegistry(PrometheusConfig config, CollectorRegistry registry, Clock clock) {
        super(clock);
        this.registry = registry;
        this.config().namingConvention(new PrometheusNamingConvention());
        this.sendDescriptions = config.descriptions();
        this.prometheusConfig = config;
    }

    /**
     * Content that should be included in the response body for an endpoint designate for
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
        io.prometheus.client.Counter counter = collectorByName(io.prometheus.client.Counter.class, getConventionName(id),
            n -> buildCollector(id, io.prometheus.client.Counter.build()));
        return new PrometheusCounter(id, counter.labels(getConventionTags(id).stream()
            .map(Tag::getValue)
            .collect(Collectors.toList())
            .toArray(new String[]{})));
    }

    @Override
    public DistributionSummary newDistributionSummary(Meter.Id id, Histogram.Builder<?> histogram, Quantiles quantiles) {
        final CustomPrometheusSummary summary = collectorByName(CustomPrometheusSummary.class, getConventionName(id),
            n -> new CustomPrometheusSummary(collectorId(id)).register(registry));
        return new PrometheusDistributionSummary(id, summary.child(getConventionTags(id), quantiles,
            buildHistogramIfNecessary(histogram)));
    }

    @Override
    protected io.micrometer.core.instrument.Timer newTimer(Meter.Id id, Histogram.Builder<?> histogram, Quantiles quantiles) {
        id.setBaseUnit("seconds");
        final CustomPrometheusSummary summary = collectorByName(CustomPrometheusSummary.class, getConventionName(id),
            n -> new CustomPrometheusSummary(collectorId(id)).register(registry));
        return new PrometheusTimer(id, summary.child(getConventionTags(id), quantiles, buildHistogramIfNecessary(histogram)), config().clock());
    }

    private <T> Histogram<T> buildHistogramIfNecessary(Histogram.Builder<T> builder) {
        if(builder == null)
            return null;

        if(builder instanceof PercentileTimeHistogram.Builder) {
            PercentileTimeHistogram.Builder percentileHistBuilder = (PercentileTimeHistogram.Builder) builder;

            // we divide nanos by 1e9 to get the fractional seconds value of the clamps
            if(prometheusConfig.timerPercentilesMax() != null) {
                double max = (double) prometheusConfig.timerPercentilesMax().toNanos() / 1e9;
                BucketFilter<Double> clampMax = BucketFilter.clampMax(max);
                percentileHistBuilder.filterBuckets(b -> b.getTag() == Double.POSITIVE_INFINITY || clampMax.shouldPublish(b));
            }

            if(prometheusConfig.timerPercentilesMin() != null) {
                double min = (double) prometheusConfig.timerPercentilesMin().toNanos() / 1e9;
                percentileHistBuilder.filterBuckets(BucketFilter.clampMin(min));
            }

            percentileHistBuilder.bucketTimeScale(TimeUnit.SECONDS);
        }
        else if(builder instanceof TimeHistogram.Builder) {
            TimeHistogram.Builder timeHist = (TimeHistogram.Builder) builder;
            timeHist.bucketTimeScale(TimeUnit.SECONDS);
        }

        return builder.create(Histogram.Summation.Cumulative);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> io.micrometer.core.instrument.Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        final WeakReference<T> ref = new WeakReference<>(obj);
        io.prometheus.client.Gauge gauge = collectorByName(Gauge.class, getConventionName(id),
            i -> buildCollector(id, io.prometheus.client.Gauge.build()));

        String[] labelValues = getConventionTags(id).stream()
            .map(Tag::getValue)
            .collect(Collectors.toList())
            .toArray(new String[]{});

        Gauge.Child child = new Gauge.Child() {
            @Override
            public double get() {
                final T obj2 = ref.get();
                return (obj2 == null) ? Double.NaN : f.applyAsDouble(obj2);
            }
        };

        gauge.setChild(child, labelValues);
        return new PrometheusGauge(id, child);
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        final CustomPrometheusLongTaskTimer longTaskTimer = collectorByName(CustomPrometheusLongTaskTimer.class, getConventionName(id),
            n -> new CustomPrometheusLongTaskTimer(collectorId(id), config().clock()).register(registry));
        return new PrometheusLongTaskTimer(id, longTaskTimer.child(getConventionTags(id)));
    }

    @Override
    protected <T> Meter newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnits) {
        FunctionTimer ft = new DefaultFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnits,
            TimeUnit.SECONDS);

        id.setBaseUnit("seconds");
        CustomPrometheusFunctionTimer pft = collectorByName(CustomPrometheusFunctionTimer.class, getConventionName(id),
            n -> new CustomPrometheusFunctionTimer(collectorId(id)).register(registry));

        pft.child(getConventionTags(id), ft);

        return ft;
    }

    @Override
    protected void newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        CustomPrometheusCollector c = (CustomPrometheusCollector) collectorMap.computeIfAbsent(getConventionName(id), name2 -> {
            Collector.Type promType = Collector.Type.UNTYPED;
            switch (type) {
                case Counter:
                    promType = Collector.Type.COUNTER;
                    break;
                case Gauge:
                    promType = Collector.Type.GAUGE;
                    break;
                case DistributionSummary:
                case Timer:
                    promType = Collector.Type.SUMMARY;
                    break;
            }

            Collector collector = new CustomPrometheusCollector(id, config().namingConvention(), promType);
            registry.register(collector);
            return collector;
        });

        c.child(getConventionTags(id), measurements);
    }

    /**
     * @return The underlying Prometheus {@link CollectorRegistry}.
     */
    public CollectorRegistry getPrometheusRegistry() {
        return registry;
    }

    private <B extends SimpleCollector.Builder<B, C>, C extends SimpleCollector<D>, D> C buildCollector(Meter.Id id,
                                                                                                        SimpleCollector.Builder<B, C> builder) {
        return builder
            .name(getConventionName(id))
            .help(!sendDescriptions || id.getDescription() == null ? " " : id.getDescription())
            .labelNames(getConventionTags(id).stream()
                .map(Tag::getKey)
                .collect(Collectors.toList())
                .toArray(new String[]{}))
            .register(registry);
    }

    private <C extends Collector> C collectorByName(Class<C> collectorType, String name, Function<String, C> ifAbsent) {
        Collector collector = collectorMap.computeIfAbsent(name, ifAbsent);
        if (!collectorType.isInstance(collector)) {
            // should never happen, because the type difference will have been caught by the registry before
            // attempting to create a new one
            throw new IllegalArgumentException("There is already a registered meter of a different type with the same name");
        }
        //noinspection unchecked
        return (C) collector;
    }

    @Override
    protected <T> io.micrometer.core.instrument.Gauge newTimeGauge(Meter.Id id, T obj, TimeUnit fUnit, ToDoubleFunction<T> f) {
        id.setBaseUnit("seconds");
        return newGauge(id, obj, obj2 -> TimeUtils.convert(f.applyAsDouble(obj2), fUnit, TimeUnit.SECONDS));
    }

    private PrometheusCollectorId collectorId(Meter.Id id) {
        return new PrometheusCollectorId(getConventionName(id),
            getConventionTags(id).stream().map(Tag::getKey).collect(toList()),
            id.getDescription());
    }
}
