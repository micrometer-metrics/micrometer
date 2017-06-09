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
package org.springframework.metrics.instrument.simple;

import org.springframework.metrics.instrument.*;
import org.springframework.metrics.instrument.Timer;
import org.springframework.metrics.instrument.internal.AbstractMeterRegistry;
import org.springframework.metrics.instrument.internal.MeterId;
import org.springframework.metrics.instrument.stats.hist.Histogram;
import org.springframework.metrics.instrument.stats.quantile.Quantiles;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.ToDoubleFunction;

import static org.springframework.metrics.instrument.internal.MapAccess.computeIfAbsent;

/**
 * A minimal meter registry implementation primarily used for tests.
 *
 * @author Jon Schneider
 */
public class SimpleMeterRegistry extends AbstractMeterRegistry {
    private final ConcurrentMap<MeterId, Meter> meterMap = new ConcurrentHashMap<>();

    public SimpleMeterRegistry() {
        this(Clock.SYSTEM);
    }

    public SimpleMeterRegistry(Clock clock) {
        super(clock);
    }

    @Override
    public Counter counter(String name, Iterable<Tag> tags) {
        return computeIfAbsent(meterMap, new MeterId(name, tags), SimpleCounter::new);
    }

    @Override
    public DistributionSummary distributionSummary(String name, Iterable<Tag> tags, Quantiles quantiles, Histogram<?> histogram) {
        registerQuantilesGaugeIfNecessary(name, tags, quantiles);
        return computeIfAbsent(meterMap, new MeterId(name, tags), SimpleDistributionSummary::new);
    }

    @Override
    protected Timer timer(String name, Iterable<Tag> tags, Quantiles quantiles, Histogram<?> histogram) {
        registerQuantilesGaugeIfNecessary(name, tags, quantiles);
        return computeIfAbsent(meterMap, new MeterId(name, tags), id -> new SimpleTimer(id, getClock()));
    }

    private void registerQuantilesGaugeIfNecessary(String name, Iterable<Tag> tags, Quantiles quantiles) {
        if(quantiles != null) {
            for (Double q : quantiles.monitored()) {
                List<Tag> quantileTags = new LinkedList<>();
                tags.forEach(quantileTags::add);
                quantileTags.add(Tag.of("quantile", Double.isNaN(q) ? "NaN" : Double.toString(q)));
                computeIfAbsent(meterMap, new MeterId(name + ".quantiles", quantileTags), id -> new SimpleGauge<>(id, q, quantiles::get));
            }
        }
    }

    @Override
    public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
        return computeIfAbsent(meterMap, new MeterId(name, tags), id -> new SimpleLongTaskTimer(id, getClock()));
    }

    @Override
    public MeterRegistry register(Meter meter) {
        meterMap.put(new MeterId(meter.getName(), meter.getTags()), meter);
        return this;
    }

    @Override
    public <T> T gauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
        computeIfAbsent(meterMap, new MeterId(name, tags), id -> new SimpleGauge<>(id, obj, f));
        return obj;
    }

    @Override
    public Collection<Meter> getMeters() {
        return meterMap.values();
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
                .map(meterMap::get)
                .filter(m -> m.getType().equals(type));
    }

    /**
     * Clear the registry of all monitored meters and their values.
     */
    public void clear() {
        meterMap.clear();
    }
}
