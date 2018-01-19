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
package io.micrometer.core.instrument.composite;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.histogram.pause.PauseDetector;
import io.micrometer.core.lang.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * The clock of the composite effectively overrides the clocks of the registries it manages without actually
 * replacing the state of the clock in these registries with the exception of long task timers, whose clock cannot
 * be overridden.
 *
 * @author Jon Schneider
 */
public class CompositeMeterRegistry extends MeterRegistry {
    private final Set<MeterRegistry> registries = ConcurrentHashMap.newKeySet();
    private final Set<MeterRegistry> unmodifiableRegistries = Collections.unmodifiableSet(registries);

    public CompositeMeterRegistry() {
        this(Clock.SYSTEM);
    }

    public CompositeMeterRegistry(Clock clock) {
        super(clock);
        config().namingConvention(NamingConvention.identity);
        config().onMeterAdded(m -> registries.forEach(((CompositeMeter) m)::add));
    }

    @Override
    protected Timer newTimer(Meter.Id id, HistogramConfig histogramConfig, PauseDetector pauseDetector) {
        return new CompositeTimer(id, clock, histogramConfig, pauseDetector);
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, HistogramConfig histogramConfig) {
        return new CompositeDistributionSummary(id, histogramConfig);
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        return new CompositeCounter(id);
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        return new CompositeLongTaskTimer(id);
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> f) {
        return new CompositeGauge<>(id, obj, f);
    }

    @Override
    protected <T> TimeGauge newTimeGauge(Meter.Id id, T obj, TimeUnit fUnit, ToDoubleFunction<T> f) {
        return new CompositeTimeGauge<>(id, obj, fUnit, f);
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnits) {
        return new CompositeFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnits);
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        return new CompositeFunctionCounter<>(id, obj, f);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }

    @Override
    protected HistogramConfig defaultHistogramConfig() {
        return HistogramConfig.NONE;
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return new CompositeCustomMeter(id, type, measurements);
    }

    public CompositeMeterRegistry add(MeterRegistry registry) {
        if(registries.add(registry)) {
            // in the event of a race, the new meter will be added to this registry via the onMeterAdded listener
            forEachMeter(m -> ((CompositeMeter) m).add(registry));
        }
        return this;
    }

    public CompositeMeterRegistry remove(MeterRegistry registry) {
        if(registries.remove(registry)) {
            forEachMeter(m -> ((CompositeMeter) m).remove(registry));
        }
        return this;
    }

    public Set<MeterRegistry> getRegistries() {
        return unmodifiableRegistries;
    }
}
