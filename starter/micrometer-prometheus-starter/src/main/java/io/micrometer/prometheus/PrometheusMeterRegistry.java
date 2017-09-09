/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.prometheus;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.prometheus.internal.CustomPrometheusCollector;
import io.micrometer.prometheus.internal.CustomPrometheusLongTaskTimer;
import io.micrometer.prometheus.internal.CustomPrometheusSummary;
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
import java.util.stream.Collectors;

/**
 * @author Jon Schneider
 */
public class PrometheusMeterRegistry extends AbstractMeterRegistry {
    private final CollectorRegistry registry;
    private final ConcurrentMap<String, Collector> collectorMap = new ConcurrentHashMap<>();
    private final boolean sendDescriptions;

    public PrometheusMeterRegistry(PrometheusConfig config) {
        this(config, new CollectorRegistry(), Clock.SYSTEM);
    }

    public PrometheusMeterRegistry(PrometheusConfig config, CollectorRegistry registry, Clock clock) {
        super(clock);
        this.registry = registry;
        this.config().namingConvention(new PrometheusNamingConvention());
        this.sendDescriptions = config.descriptions();
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
        io.prometheus.client.Counter counter = collectorByName(io.prometheus.client.Counter.class, id.getConventionName(),
            n -> buildCollector(id, io.prometheus.client.Counter.build()));
        return new PrometheusCounter(id, counter.labels(id.getConventionTags().stream()
            .map(Tag::getValue)
            .collect(Collectors.toList())
            .toArray(new String[]{})));
    }

    @Override
    public DistributionSummary newDistributionSummary(Meter.Id id, Histogram.Builder<?> histogram, Quantiles quantiles) {
        final CustomPrometheusSummary summary = collectorByName(CustomPrometheusSummary.class, id.getConventionName(),
            n -> new CustomPrometheusSummary(id).register(registry));
        return new PrometheusDistributionSummary(id, summary.child(id.getConventionTags(), quantiles,
            buildHistogramIfNecessary(histogram)));
    }

    @Override
    protected io.micrometer.core.instrument.Timer newTimer(Meter.Id id, Histogram.Builder<?> histogram, Quantiles quantiles) {
        id.setBaseUnit("seconds");
        final CustomPrometheusSummary summary = collectorByName(CustomPrometheusSummary.class, id.getConventionName(),
            n -> new CustomPrometheusSummary(id).register(registry));
        return new PrometheusTimer(id, summary.child(id.getConventionTags(), quantiles,
            buildHistogramIfNecessary(histogram)), config().clock());
    }

    private Histogram<?> buildHistogramIfNecessary(Histogram.Builder<?> histogram) {
        return histogram == null ? null : histogram.create(TimeUnit.SECONDS, Histogram.Type.Cumulative);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> io.micrometer.core.instrument.Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        final WeakReference<T> ref = new WeakReference<>(obj);
        io.prometheus.client.Gauge gauge = collectorByName(Gauge.class, id.getConventionName(),
            i -> buildCollector(id, io.prometheus.client.Gauge.build()));

        String[] labelValues = id.getConventionTags().stream()
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
        final CustomPrometheusLongTaskTimer longTaskTimer = collectorByName(CustomPrometheusLongTaskTimer.class, id.getConventionName(),
            n -> new CustomPrometheusLongTaskTimer(id, config().clock()).register(registry));
        return new PrometheusLongTaskTimer(id, longTaskTimer.child(id.getConventionTags()));
    }

    @Override
    protected void newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        CustomPrometheusCollector c = (CustomPrometheusCollector) collectorMap.computeIfAbsent(id.getConventionName(), name2 -> {
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

            Collector collector = new CustomPrometheusCollector(id, promType);
            registry.register(collector);
            return collector;
        });

        c.child(id.getConventionTags(), measurements);
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
            .name(id.getConventionName())
            .help(!sendDescriptions || id.getDescription() == null ? " " : id.getDescription())
            .labelNames(id.getConventionTags().stream()
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
}
