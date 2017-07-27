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
package io.micrometer.core.instrument.dropwizard;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleLongTaskTimer;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.core.instrument.util.MapAccess;
import io.micrometer.core.instrument.util.MeterId;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.ToDoubleFunction;

/**
 * @author Jon Schneider
 */
public class DropwizardMeterRegistry extends AbstractMeterRegistry {
    private final MetricRegistry registry;
    private final ConcurrentMap<MeterId, Meter> meterMap = new ConcurrentHashMap<>();
    private final HierarchicalNameMapper nameMapper;

    public DropwizardMeterRegistry(HierarchicalNameMapper nameMapper, Clock clock) {
        super(clock);
        this.registry = new MetricRegistry();
        this.nameMapper = nameMapper;
    }

    public MetricRegistry getDropwizardRegistry() {
        return registry;
    }

    @Override
    public Collection<Meter> getMeters() {
        return meterMap.values();
    }

    @Override
    public <M extends Meter> Optional<M> findMeter(Class<M> mClass, String name, Iterable<Tag> tags) {
        Collection<Tag> tagsToMatch = new ArrayList<>();
        tags.forEach(tagsToMatch::add);

        return meterMap.keySet().stream()
                .filter(id -> id.getName().equals(name))
                .filter(id -> id.getTags().containsAll(tagsToMatch))
                .findAny()
                .map(meterMap::get)
                .filter(mClass::isInstance)
                .map(mClass::cast);
    }

    @Override
    public Optional<Meter> findMeter(Meter.Type type, String name, Iterable<Tag> tags) {
        Collection<Tag> tagsToMatch = new ArrayList<>();
        tags.forEach(tagsToMatch::add);

        return meterMap.keySet().stream()
                .filter(id -> id.getName().equals(name))
                .filter(id -> id.getTags().containsAll(tagsToMatch))
                .findAny()
                .map(meterMap::get)
                .filter(m -> m.getType().equals(type));
    }

    @Override
    public Counter counter(String name, Iterable<Tag> tags) {
        return MapAccess.computeIfAbsent(meterMap, new MeterId(name, withCommonTags(tags)),
                id -> new DropwizardCounter(id, registry.meter(toDropwizardName(id))));
    }

    @Override
    public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
        return MapAccess.computeIfAbsent(meterMap, new MeterId(name, withCommonTags(tags)),
                id -> {
                    LongTaskTimer ltt = new SimpleLongTaskTimer(id, clock);
                    registry.register(nameMapper.toHierarchicalName(name + "_active", id.getTags()),
                            (Gauge<Integer>) ltt::activeTasks);
                    registry.register(nameMapper.toHierarchicalName(name + "_duration", id.getTags()),
                            (Gauge<Long>) ltt::duration);
                    return ltt;
                });
    }

    @Override
    public MeterRegistry register(Meter meter) {
        MapAccess.computeIfAbsent(meterMap, new MeterId(meter.getName(), meter.getTags()), id -> {
            for (int i = 0; i < meter.measure().size(); i++) {
                final int index = i;
                registry.register(toDropwizardName(id), (Gauge<Double>) () -> meter.measure().get(index).getValue());
            }
            return meter;
        });

        return this;
    }

    @Override
    public <T> T gauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
        MapAccess.computeIfAbsent(meterMap, new MeterId(name, withCommonTags(tags)), id -> {
            final WeakReference<T> ref = new WeakReference<>(obj);
            Gauge<Double> gauge = () -> f.applyAsDouble(ref.get());
            registry.register(toDropwizardName(id), gauge);
            return new DropwizardGauge(id, gauge);
        });
        return obj;
    }

    private String toDropwizardName(MeterId id) {
        return nameMapper.toHierarchicalName(id.getName(), id.getTags());
    }

    @Override
    protected Timer timer(String name, Iterable<Tag> tags, Quantiles quantiles, Histogram<?> histogram) {
        return MapAccess.computeIfAbsent(meterMap, new MeterId(name, withCommonTags(tags)),
                id -> new DropwizardTimer(id, registry.timer(toDropwizardName(id)), clock));
    }

    @Override
    protected DistributionSummary distributionSummary(String name, Iterable<Tag> tags, Quantiles quantiles, Histogram<?> histogram) {
        // FIXME deal with quantiles, histogram
        return MapAccess.computeIfAbsent(meterMap, new MeterId(name, withCommonTags(tags)),
                id -> new DropwizardDistributionSummary(id, registry.histogram(toDropwizardName(id))));
    }
}
