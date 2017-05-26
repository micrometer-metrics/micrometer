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
import org.springframework.metrics.instrument.stats.Quantiles;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;

/**
 * A minimal meter registry implementation primarily used for tests.
 *
 * @author Jon Schneider
 */
public class SimpleMeterRegistry extends AbstractMeterRegistry {
    private final Map<MeterId, Meter> meterMap = new ConcurrentHashMap<>();
    private final Map<Meter, MeterId> idMap = new HashMap<>();

    public SimpleMeterRegistry() {
        this(Clock.SYSTEM);
    }

    public SimpleMeterRegistry(Clock clock) {
        super(clock);
    }

    @Override
    public Counter counter(String name, Iterable<Tag> tags) {
        return (Counter) meterMap.computeIfAbsent(new MeterId(name, tags), id -> storeId(id, new SimpleCounter(name)));
    }

    @Override
    public DistributionSummary distributionSummary(String name, Iterable<Tag> tags, Quantiles quantiles) {
        registerQuantilesGaugeIfNecessary(name, tags, quantiles);
        return (DistributionSummary) meterMap.computeIfAbsent(new MeterId(name, tags), id -> storeId(id, new SimpleDistributionSummary(name)));
    }

    @Override
    protected Timer timer(String name, Iterable<Tag> tags, Quantiles quantiles) {
        registerQuantilesGaugeIfNecessary(name, tags, quantiles);
        return (Timer) meterMap.computeIfAbsent(new MeterId(name, tags), id -> storeId(id, new SimpleTimer(name)));
    }

    private void registerQuantilesGaugeIfNecessary(String name, Iterable<Tag> tags, Quantiles quantiles) {
        if(quantiles != null) {
            for (Double q : quantiles.monitored()) {
                List<Tag> quantileTags = new LinkedList<>();
                tags.forEach(quantileTags::add);
                quantileTags.add(Tag.of("quantile", Double.isNaN(q) ? "NaN" : Double.toString(q)));
                meterMap.computeIfAbsent(new MeterId(name + ".quantiles", quantileTags), id -> storeId(id, new SimpleGauge<>(name, q, quantiles::get)));
            }
        }
    }

    @Override
    public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
        return (LongTaskTimer) meterMap.computeIfAbsent(new MeterId(name, tags), id -> storeId(id, new SimpleLongTaskTimer(name, getClock())));
    }

    @Override
    public <T> T gauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
        meterMap.computeIfAbsent(new MeterId(name, tags), id -> storeId(id, new SimpleGauge<>(name, obj, f)));
        return obj;
    }

    @Override
    public Collection<Meter> getMeters() {
        return meterMap.values();
    }

    public MeterId id(Meter m) {
        return idMap.get(m);
    }

    public <M extends Meter> Optional<M> findMeter(Class<M> mClass, String name, Iterable<Tag> tags) {
        Collection<Tag> tagsToMatch = new ArrayList<>();
        tags.forEach(tagsToMatch::add);

        //noinspection unchecked
        return meterMap.keySet().stream()
                .filter(id -> id.getName().equals(name))
                .filter(id ->
                        Arrays.asList(id.getTags()).containsAll(tagsToMatch))
                .findAny()
                .map(meterMap::get)
                .map(m -> (M) m);
    }

    private Meter storeId(MeterId id, Meter m) {
        idMap.put(m, id);
        return m;
    }

    /**
     * Clear the registry of all monitored meters and their values.
     */
    public void clear() {
        meterMap.clear();
        idMap.clear();
    }
}
