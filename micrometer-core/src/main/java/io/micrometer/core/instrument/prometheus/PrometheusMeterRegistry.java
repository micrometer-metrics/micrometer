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
package io.micrometer.core.instrument.prometheus;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.prometheus.internal.CustomPrometheusCollector;
import io.micrometer.core.instrument.prometheus.internal.CustomPrometheusLongTaskTimer;
import io.micrometer.core.instrument.prometheus.internal.CustomPrometheusSummary;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;
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
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import static java.util.stream.StreamSupport.stream;

/**
 * @author Jon Schneider
 */
public class PrometheusMeterRegistry extends AbstractMeterRegistry {
    private final CollectorRegistry registry;
    private final ConcurrentMap<String, Collector> collectorMap = new ConcurrentHashMap<>();

    public PrometheusMeterRegistry() {
        this(new CollectorRegistry());
    }

    public PrometheusMeterRegistry(CollectorRegistry registry) {
        this(registry, Clock.SYSTEM);
    }

    public PrometheusMeterRegistry(CollectorRegistry registry, Clock clock) {
        super(clock, new PrometheusTagFormatter());
        this.registry = registry;
        this.config().namingConvention(new PrometheusNamingConvention());
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
    public Counter newCounter(String name, Iterable<Tag> tags) {
        io.prometheus.client.Counter counter = collectorByName(io.prometheus.client.Counter.class, name,
            n -> buildCollector(name, tags, io.prometheus.client.Counter.build()));
        return new PrometheusCounter(name, tags, child(counter, tags));
    }

    @Override
    public DistributionSummary newDistributionSummary(String name, Iterable<Tag> tags, Quantiles quantiles, Histogram<?> histogram) {
        final CustomPrometheusSummary summary = collectorByName(CustomPrometheusSummary.class, name,
            n -> new CustomPrometheusSummary(name, tags).register(registry));
        return new PrometheusDistributionSummary(name, tags, summary.child(tags, quantiles, histogram));
    }

    @Override
    protected io.micrometer.core.instrument.Timer newTimer(String name, Iterable<Tag> tags, Quantiles quantiles, Histogram<?> histogram) {
        final CustomPrometheusSummary summary = collectorByName(CustomPrometheusSummary.class, name,
            n -> new CustomPrometheusSummary(name, tags).register(registry));
        return new PrometheusTimer(name, tags, summary.child(tags, quantiles, histogram), config().clock());
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> io.micrometer.core.instrument.Gauge newGauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
        final WeakReference<T> ref = new WeakReference<>(obj);
        io.prometheus.client.Gauge gauge = collectorByName(Gauge.class, name,
            i -> buildCollector(name, tags, io.prometheus.client.Gauge.build()));

        String[] labelValues = stream(tags.spliterator(), false)
            .map(Tag::getValue)
            .collect(Collectors.toList())
            .toArray(new String[]{});

        Gauge.Child child = new Gauge.Child() {
            @Override
            public double get() {
                final T obj = ref.get();
                return (obj == null) ? Double.NaN : f.applyAsDouble(obj);
            }
        };

        gauge.setChild(child, labelValues);
        return new PrometheusGauge(name, tags, child);
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(String name, Iterable<Tag> tags) {
        final CustomPrometheusLongTaskTimer longTaskTimer = collectorByName(CustomPrometheusLongTaskTimer.class, name,
            n -> new CustomPrometheusLongTaskTimer(name, tags, config().clock()).register(registry));
        return new PrometheusLongTaskTimer(name, tags, longTaskTimer.child(tags));
    }

    @Override
    protected void newMeter(String name, Iterable<Tag> tags, Meter.Type type, Iterable<Measurement> measurements) {
        CustomPrometheusCollector c = (CustomPrometheusCollector) collectorMap.computeIfAbsent(name, name2 -> {
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

            Collector collector = new CustomPrometheusCollector(name, tags, promType);
            registry.register(collector);
            return collector;
        });

        c.child(tags, measurements);
    }

    /**
     * @return The underlying Prometheus {@link CollectorRegistry}.
     */
    public CollectorRegistry getPrometheusRegistry() {
        return registry;
    }

    private <B extends SimpleCollector.Builder<B, C>, C extends SimpleCollector<D>, D> C buildCollector(String name, Iterable<Tag> tags,
                                                                                                        SimpleCollector.Builder<B, C> builder) {
        return builder
            .name(name)
            .help(" ")
            .labelNames(stream(tags.spliterator(), false)
                .map(Tag::getKey)
                .collect(Collectors.toList())
                .toArray(new String[]{}))
            .register(registry);
    }

    private <C extends SimpleCollector<D>, D> D child(C collector, Iterable<Tag> tags) {
        return collector.labels(stream(tags.spliterator(), false)
            .map(Tag::getValue)
            .collect(Collectors.toList())
            .toArray(new String[]{}));
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
}
