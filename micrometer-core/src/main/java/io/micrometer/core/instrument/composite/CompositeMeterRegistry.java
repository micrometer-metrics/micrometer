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
package io.micrometer.core.instrument.composite;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;
import io.micrometer.core.instrument.util.MapAccess;
import io.micrometer.core.instrument.util.MeterId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.ToDoubleFunction;

import static java.util.stream.Collectors.toList;

/**
 * The clock of the composite effectively overrides the clocks of the registries it manages without actually
 * replacing the state of the clock in these registries with the exception of long task timers, whose clock cannot
 * be overridden.
 *
 * @author Jon Schneider
 */
public class CompositeMeterRegistry extends AbstractMeterRegistry {
    private final Set<MeterRegistry> registries = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<MeterId, CompositeMeter> meterMap = new ConcurrentHashMap<>();

    public CompositeMeterRegistry() {
        this(Clock.SYSTEM);
    }

    public CompositeMeterRegistry(Clock clock) {
        super(clock);
    }

    @Override
    protected Timer newTimer(String name, Iterable<Tag> tags, Quantiles quantiles, Histogram<?> histogram) {
        return MapAccess.computeIfAbsent(meterMap, new MeterId(name, Tags.concat(tags, commonTags)), id -> {
            CompositeTimer timer = new CompositeTimer(id, quantiles, histogram, clock);
            registries.forEach(timer::add);
            return timer;
        });
    }

    @Override
    protected DistributionSummary newDistributionSummary(String name, Iterable<Tag> tags, Quantiles quantiles, Histogram<?> histogram) {
        return MapAccess.computeIfAbsent(meterMap, new MeterId(name, Tags.concat(tags, commonTags)), id -> {
            CompositeDistributionSummary ds = new CompositeDistributionSummary(id, quantiles, histogram);
            registries.forEach(ds::add);
            return ds;
        });
    }

    @Override
    public Collection<Meter> getMeters() {
        return meterMap.values().stream().map(Meter.class::cast).collect(toList());
    }

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

    public Optional<Meter> findMeter(Meter.Type type, String name, Iterable<Tag> tags) {
        Collection<Tag> tagsToMatch = new ArrayList<>();
        tags.forEach(tagsToMatch::add);

        return meterMap.keySet().stream()
            .filter(id -> id.getName().equals(name))
            .filter(id -> id.getTags().containsAll(tagsToMatch))
            .findAny()
            .map(id -> (Meter) meterMap.get(id))
            .filter(m -> m.getType().equals(type));
    }

    @Override
    public Counter counter(String name, Iterable<Tag> tags) {
        return MapAccess.computeIfAbsent(meterMap, new MeterId(name, Tags.concat(tags, commonTags)), id -> {
            CompositeCounter counter = new CompositeCounter(id);
            registries.forEach(counter::add);
            return counter;
        });
    }

    @Override
    public <T> T counter(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
        MapAccess.computeIfAbsent(meterMap, new MeterId(name, Tags.concat(tags, commonTags)), id -> {
           CompositeFunctionCounter<T> counter = new CompositeFunctionCounter<>(id, obj, f);
           registries.forEach(counter::add);
           return counter;
        });
        return obj;
    }

    @Override
    public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
        return MapAccess.computeIfAbsent(meterMap, new MeterId(name, Tags.concat(tags, commonTags)), id -> {
            CompositeLongTaskTimer longTaskTimer = new CompositeLongTaskTimer(id);
            registries.forEach(longTaskTimer::add);
            return longTaskTimer;
        });
    }

    @Override
    public MeterRegistry register(Meter meter) {
        MapAccess.computeIfAbsent(meterMap, new MeterId(meter.getName(), Tags.concat(meter.getTags(), commonTags)), id -> {
            CompositeCustomMeter compositeMeter = new CompositeCustomMeter(meter);
            registries.forEach(compositeMeter::add);
            return compositeMeter;
        });
        return this;
    }

    @Override
    protected <T> Gauge newGauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
        return MapAccess.computeIfAbsent(meterMap, new MeterId(name, Tags.concat(tags, commonTags)), id -> {
            CompositeGauge<T> gauge = new CompositeGauge<>(id, obj, f);
            registries.forEach(gauge::add);
            return gauge;
        });
    }

    public void add(MeterRegistry registry) {
        if(registries.add(registry)) {
            meterMap.values().forEach(m -> m.add(registry));
        }
    }

    public void remove(MeterRegistry registry) {
        if(registries.remove(registry)) {
            meterMap.values().forEach(m -> m.remove(registry));
        }
    }
}
