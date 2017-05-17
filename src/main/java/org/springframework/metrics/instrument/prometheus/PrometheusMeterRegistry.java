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
package org.springframework.metrics.instrument.prometheus;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.SimpleCollector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.metrics.instrument.*;
import org.springframework.metrics.instrument.Timer;
import org.springframework.metrics.instrument.internal.AbstractMeterRegistry;
import org.springframework.metrics.instrument.internal.ImmutableTag;
import org.springframework.metrics.instrument.internal.MeterId;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import static java.util.stream.StreamSupport.stream;
import static org.springframework.metrics.instrument.internal.MeterId.id;

public class PrometheusMeterRegistry extends AbstractMeterRegistry {
    private final CollectorRegistry registry;

    private final Map<String, Collector> collectorMap = new ConcurrentHashMap<>();
    private final Map<MeterId, Meter> meterMap = new ConcurrentHashMap<>();

    public PrometheusMeterRegistry() {
        this(CollectorRegistry.defaultRegistry);
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
    public <M extends Meter> Optional<M> findMeter(Class<M> mClass, String name, Iterable<Tag> tags) {
        Collection<Tag> tagsToMatch = new ArrayList<>();
        tags.forEach(tagsToMatch::add);

        //noinspection unchecked
        return meterMap.keySet().stream()
                .filter(id -> id.getName().equals(name))
                .filter(id -> Arrays.asList(id.getTags()).containsAll(tagsToMatch))
                .findAny()
                .map(meterMap::get)
                .map(m -> (M) m);
    }

    @Override
    public Counter counter(String name, Iterable<Tag> tags) {
        MeterId id = id(name, tags);
        io.prometheus.client.Counter counter = (io.prometheus.client.Counter) collectorMap.computeIfAbsent(name,
                i -> buildCollector(id, io.prometheus.client.Counter.build()));

        return (Counter) meterMap.computeIfAbsent(id, c -> new PrometheusCounter(name, child(counter, id.getTags())));
    }

    @Override
    public DistributionSummary distributionSummary(String name, Iterable<Tag> tags) {
        MeterId id = id(name, tags);
        io.prometheus.client.Summary summary = (io.prometheus.client.Summary) collectorMap.computeIfAbsent(name,
                i -> buildCollector(id, io.prometheus.client.Summary.build()));

        return (DistributionSummary) meterMap.computeIfAbsent(id, s -> new PrometheusDistributionSummary(name, child(summary, id.getTags())));
    }

    @Override
    public Timer timer(String name, Iterable<Tag> tags) {
        MeterId id = id(name, tags);
        io.prometheus.client.Summary summary = (io.prometheus.client.Summary) collectorMap.computeIfAbsent(name,
                i -> buildCollector(id, io.prometheus.client.Summary.build()));

        return (Timer) meterMap.computeIfAbsent(id, s -> new PrometheusTimer(name, child(summary, id.getTags()), getClock()));
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
        io.prometheus.client.Gauge gauge = (io.prometheus.client.Gauge) collectorMap.computeIfAbsent(name,
                i -> buildCollector(id, io.prometheus.client.Gauge.build()));

        meterMap.computeIfAbsent(id, g -> {
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
