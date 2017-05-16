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
package org.springframework.metrics.instrument.prometheus;

import io.prometheus.client.*;
import io.prometheus.client.Gauge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.metrics.instrument.*;
import org.springframework.metrics.instrument.Counter;
import org.springframework.metrics.instrument.internal.AbstractMeterRegistry;
import org.springframework.metrics.instrument.internal.MeterId;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import static org.springframework.metrics.instrument.internal.MeterId.id;

public class PrometheusMeterRegistry extends AbstractMeterRegistry {
    private final CollectorRegistry registry;

    private final Map<MeterId, Collector> collectorMap = new HashMap<>();

    // Map of Collector Child (which has no common base class or interface) to Meter
    private final Map<Object, Meter> meterMap = new HashMap<>();

    public PrometheusMeterRegistry() {
        this(new CollectorRegistry(true));
    }

    public PrometheusMeterRegistry(CollectorRegistry registry) {
        this(registry, Clock.SYSTEM);
    }

    @Autowired
    public PrometheusMeterRegistry(CollectorRegistry registry, Clock clock) {
        super(clock);
        this.registry = registry;
    }

    @Override
    public Collection<Meter> getMeters() {
        return meterMap.values();
    }

    @Override
    public Counter counter(String name, Iterable<Tag> tags) {
        MeterId id = id(name, tags);
        io.prometheus.client.Counter counter = (io.prometheus.client.Counter) collectorMap.computeIfAbsent(id,
                i -> buildCollector(id, io.prometheus.client.Counter.build()));

        return (Counter) meterMap.computeIfAbsent(counter, c -> new PrometheusCounter(name, child(counter, id.getTags())));
    }

    @Override
    public DistributionSummary distributionSummary(String name, Iterable<Tag> tags) {
        MeterId id = id(name, tags);
        io.prometheus.client.Summary summary = (io.prometheus.client.Summary) collectorMap.computeIfAbsent(id(name, tags),
                i -> buildCollector(id, io.prometheus.client.Summary.build()));

        return (DistributionSummary) meterMap.computeIfAbsent(summary, s -> new PrometheusDistributionSummary(name, child(summary, id.getTags())));
    }

    @Override
    public Timer timer(String name, Iterable<Tag> tags) {
        MeterId id = id(name, tags);
        io.prometheus.client.Summary summary = (io.prometheus.client.Summary) collectorMap.computeIfAbsent(id(name, tags),
                i -> buildCollector(id, io.prometheus.client.Summary.build()));

        return (Timer) meterMap.computeIfAbsent(summary, s -> new PrometheusTimer(name, child(summary, id.getTags()), getClock()));
    }

    @Override
    public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
        return new PrometheusLongTaskTimer(name, tags, getClock());
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T gauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
        final WeakReference<T> ref = new WeakReference<>(obj);

        MeterId id = id(name, tags);
        io.prometheus.client.Gauge gauge = (io.prometheus.client.Gauge) collectorMap.computeIfAbsent(id(name, tags),
                i -> buildCollector(id, io.prometheus.client.Gauge.build()));

        meterMap.computeIfAbsent(gauge, g -> {
            gauge.setChild(new Gauge.Child() {
                @Override
                public double get() {
                    final T obj = ref.get();
                    return (obj == null) ? Double.NaN : f.applyAsDouble(obj);
                }
            });
            return new PrometheusGauge(name, child(gauge, id.getTags()));
        });

        return obj;
    }

    private <B extends SimpleCollector.Builder<B, C>, C extends SimpleCollector<D>, D> C buildCollector(MeterId id,
                                                                                                        SimpleCollector.Builder<B, C> builder) {
        return builder
                .name(id.getName())
                .help(" ")
                .labelNames(Arrays.stream(id.getTags())
                        .map(Tag::getKey)
                        .collect(Collectors.toList())
                        .toArray(new String[]{}))
                .register(registry);
    }

    private <C extends SimpleCollector<D>, D> D child(C collector, Tag[] tags) {
        return collector.labels(Arrays.stream(tags)
                .map(Tag::getValue)
                .collect(Collectors.toList())
                .toArray(new String[]{}));
    }
}
