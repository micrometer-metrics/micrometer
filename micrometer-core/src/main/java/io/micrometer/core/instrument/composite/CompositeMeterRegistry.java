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
import java.util.concurrent.TimeUnit;
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
        super(clock);
        config().namingConvention(NamingConvention.identity);
    }

    @Override
    protected Timer newTimer(Meter.Id id, Histogram.Builder<?> histogram, Quantiles quantiles) {
        CompositeTimer timer = new CompositeTimer(id, quantiles, histogram, clock);
        compositeMeters.add(timer);
        registries.forEach(timer::add);
        return timer;
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, Histogram.Builder<?> histogram, Quantiles quantiles) {
        CompositeDistributionSummary ds = new CompositeDistributionSummary(id, quantiles, histogram);
        compositeMeters.add(ds);
        registries.forEach(ds::add);
        return ds;
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        CompositeCounter counter = new CompositeCounter(id);
        compositeMeters.add(counter);
        registries.forEach(counter::add);
        return counter;
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        CompositeLongTaskTimer longTaskTimer = new CompositeLongTaskTimer(id);
        compositeMeters.add(longTaskTimer);
        registries.forEach(longTaskTimer::add);
        return longTaskTimer;
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        CompositeGauge<T> gauge = new CompositeGauge<>(id, obj, f);
        compositeMeters.add(gauge);
        registries.forEach(gauge::add);
        return gauge;
    }

    @Override
    protected <T> Gauge newTimeGauge(Meter.Id id, T obj, TimeUnit fUnit, ToDoubleFunction<T> f) {
        CompositeTimeGauge<T> gauge = new CompositeTimeGauge<>(id, obj, fUnit, f);
        compositeMeters.add(gauge);
        registries.forEach(gauge::add);
        return gauge;
    }

    @Override
    protected void newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        CompositeMeter meter = new CompositeCustomMeter(id, type, measurements);
        compositeMeters.add(meter);
        registries.forEach(meter::add);
    }

    public CompositeMeterRegistry add(MeterRegistry registry) {
        if(registries.add(registry)) {
            compositeMeters.forEach(m -> m.add(registry));
        }
        return this;
    }

    public CompositeMeterRegistry remove(MeterRegistry registry) {
        if(registries.remove(registry)) {
            compositeMeters.forEach(m -> m.remove(registry));
        }
        return this;
    }

    public Set<MeterRegistry> getRegistries() {
        return registries;
    }
}
