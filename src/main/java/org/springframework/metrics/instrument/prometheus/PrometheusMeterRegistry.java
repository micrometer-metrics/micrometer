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

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.SimpleCollector;
import io.prometheus.client.Summary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.metrics.instrument.*;
import org.springframework.metrics.instrument.internal.AbstractMeterRegistry;

import java.lang.ref.WeakReference;
import java.util.function.ToDoubleFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class PrometheusMeterRegistry extends AbstractMeterRegistry {
    private CollectorRegistry registry;

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
    public Counter counter(String name, Iterable<Tag> tags) {
        return register(new PrometheusCounter(name, withNameAndTags(io.prometheus.client.Gauge.build(), name, tags)));
    }

    @Override
    public DistributionSummary distributionSummary(String name, Iterable<Tag> tags) {
        return register(new PrometheusDistributionSummary(name, withNameAndTags(Summary.build(), name, tags)));
    }

    @Override
    public Timer timer(String name, Iterable<Tag> tags) {
        return register(new PrometheusTimer(name, withNameAndTags(Summary.build(), name, tags), getClock()));
    }

    @Override
    public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
        return new PrometheusLongTaskTimer(name, tags, getClock());
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T gauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
        final WeakReference<T> ref = new WeakReference<T>(obj);

        register(new PrometheusGauge(name, withNameAndTags(io.prometheus.client.Gauge.build(), name, tags,
                gauge -> gauge.setChild(new Gauge.Child() {
                    @Override
                    public double get() {
                        final T obj = ref.get();
                        return (obj == null) ? Double.NaN : f.applyAsDouble(obj);
                    }
                }))));
        return obj;
    }

    private <B extends SimpleCollector.Builder<B, C>, C extends SimpleCollector<D>, D> D withNameAndTags(
            SimpleCollector.Builder<B, C> builder, String name, Iterable<Tag> tags) {
        return withNameAndTags(builder, name, tags, UnaryOperator.identity());
    }

    private <B extends SimpleCollector.Builder<B, C>, C extends SimpleCollector<D>, D> D withNameAndTags(
            SimpleCollector.Builder<B, C> builder, String name, Iterable<Tag> tags, UnaryOperator<C> collectorTransform) {
        C collector = builder
                .name(name)
                .help(" ")
                .labelNames(StreamSupport.stream(tags.spliterator(), false)
                        .map(Tag::getKey)
                        .collect(Collectors.toList())
                        .toArray(new String[]{}))
                .register(registry);

        collector = collectorTransform.apply(collector);

        return collector.labels(StreamSupport.stream(tags.spliterator(), false)
                .map(Tag::getValue)
                .collect(Collectors.toList())
                .toArray(new String[]{}));
    }
}
