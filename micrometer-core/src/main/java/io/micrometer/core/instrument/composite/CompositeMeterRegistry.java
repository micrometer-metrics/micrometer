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
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToDoubleFunction;

/**
 * The clock of the composite effectively overrides the clocks of the registries it manages without actually
 * replacing the state of the clock in these registries with the exception of long task timers, whose clock cannot
 * be overridden.
 *
 * @author Jon Schneider
 */
public class CompositeMeterRegistry extends AbstractMeterRegistry {
    private final Set<MeterRegistry> registries = ConcurrentHashMap.newKeySet();
    private Collection<CompositeMeter> compositeMeters = new CopyOnWriteArrayList<>();

    public CompositeMeterRegistry() {
        this(Clock.SYSTEM);
    }

    public CompositeMeterRegistry(Clock clock) {
        super(clock, TagFormatter.identity);
    }

    @Override
    protected Timer newTimer(String name, Iterable<Tag> tags, Quantiles quantiles, Histogram<?> histogram) {
        CompositeTimer timer = new CompositeTimer(name, tags, quantiles, histogram, clock);
        compositeMeters.add(timer);
        registries.forEach(timer::add);
        return timer;
    }

    @Override
    protected DistributionSummary newDistributionSummary(String name, Iterable<Tag> tags, Quantiles quantiles, Histogram<?> histogram) {
        CompositeDistributionSummary ds = new CompositeDistributionSummary(name, tags, quantiles, histogram);
        compositeMeters.add(ds);
        registries.forEach(ds::add);
        return ds;
    }

    @Override
    protected Counter newCounter(String name, Iterable<Tag> tags) {
        CompositeCounter counter = new CompositeCounter(name, tags);
        compositeMeters.add(counter);
        registries.forEach(counter::add);
        return counter;
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(String name, Iterable<Tag> tags) {
        CompositeLongTaskTimer longTaskTimer = new CompositeLongTaskTimer(name, tags);
        compositeMeters.add(longTaskTimer);
        registries.forEach(longTaskTimer::add);
        return longTaskTimer;
    }

    @Override
    protected <T> Gauge newGauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
        CompositeGauge<T> gauge = new CompositeGauge<>(name, tags, obj, f);
        compositeMeters.add(gauge);
        registries.forEach(gauge::add);
        return gauge;
    }

    @Override
    protected void newMeter(String name, Iterable<Tag> tags, Meter.Type type, Iterable<Measurement> measurements) {
        CompositeMeter meter = new CompositeCustomMeter(name, tags, type, measurements);
        compositeMeters.add(meter);
        registries.forEach(meter::add);
    }

    public void add(MeterRegistry registry) {
        if(registries.add(registry)) {
            compositeMeters.forEach(m -> m.add(registry));
        }
    }

    public void remove(MeterRegistry registry) {
        if(registries.remove(registry)) {
            compositeMeters.forEach(m -> m.remove(registry));
        }
    }
}
