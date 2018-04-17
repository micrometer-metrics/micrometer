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
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.lang.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final AtomicBoolean registriesLock = new AtomicBoolean(false);
    private final Set<MeterRegistry> registries = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<MeterRegistry> unmodifiableRegistries = Collections.unmodifiableSet(registries);

    // VisibleForTesting
    volatile Set<MeterRegistry> nonCompositeDescendants = Collections.emptySet();

    private volatile Set<CompositeMeterRegistry> parents = Collections.newSetFromMap(new IdentityHashMap<>());

    public CompositeMeterRegistry() {
        this(Clock.SYSTEM);
    }

    public CompositeMeterRegistry(Clock clock) {
        super(clock);
        config().namingConvention(NamingConvention.identity);
        config().onMeterAdded(m -> {
            if (m instanceof CompositeMeter) { // should always be
                for (; ; ) {
                    if (!registriesLock.get()) {
                        nonCompositeDescendants.forEach(((CompositeMeter) m)::add);
                        break;
                    }
                }
            }
        });
    }

    public CompositeMeterRegistry(Clock clock, Iterable<MeterRegistry> registries) {
        this(clock);
        registries.forEach(this::add);
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        return new CompositeTimer(id, clock, distributionStatisticConfig, pauseDetector);
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        return new CompositeDistributionSummary(id, distributionStatisticConfig, scale);
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
    protected <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        return new CompositeGauge<>(id, obj, valueFunction);
    }

    @Override
    protected <T> TimeGauge newTimeGauge(Meter.Id id, T obj, TimeUnit valueFunctionUnit, ToDoubleFunction<T> valueFunction) {
        return new CompositeTimeGauge<>(id, obj, valueFunctionUnit, valueFunction);
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnits) {
        return new CompositeFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnits);
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        return new CompositeFunctionCounter<>(id, obj, countFunction);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.NONE;
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return new CompositeCustomMeter(id, type, measurements);
    }


    public CompositeMeterRegistry add(MeterRegistry registry) {
        forbidSelfContainingComposite(registry);

        for (; ; ) {
            if (registriesLock.compareAndSet(false, true)) {
                try {
                    if (registry instanceof CompositeMeterRegistry) {
                        ((CompositeMeterRegistry) registry).parents.add(this);
                    }

                    if (registries.add(registry)) {
                        updateDescendants(false);
                    }
                    break;
                } finally {
                    registriesLock.set(false);
                }
            }
        }

        return this;
    }

    private void forbidSelfContainingComposite(MeterRegistry registry) {
        if (registry == this) {
            throw new IllegalArgumentException("Adding a composite meter registry to itself is not allowed!");
        }

        if (registry instanceof CompositeMeterRegistry) {
            ((CompositeMeterRegistry) registry).getRegistries().forEach(this::forbidSelfContainingComposite);
        }
    }

    public CompositeMeterRegistry remove(MeterRegistry registry) {
        for (; ; ) {
            if (registriesLock.compareAndSet(false, true)) {
                try {
                    if (registry instanceof CompositeMeterRegistry) {
                        ((CompositeMeterRegistry) registry).parents.remove(this);
                    }

                    if (registries.remove(registry)) {
                        updateDescendants(false);
                    }
                    break;
                } finally {
                    registriesLock.set(false);
                }
            }
        }

        return this;
    }

    void updateDescendants(boolean lock) {
        Set<MeterRegistry> descendants = Collections.newSetFromMap(new IdentityHashMap<>());
        for (; ; ) {
            if (registriesLock.compareAndSet(!lock, true)) {
                try {
                    for (MeterRegistry r : registries) {
                        if (r instanceof CompositeMeterRegistry) {
                            descendants.addAll(((CompositeMeterRegistry) r).nonCompositeDescendants);
                        } else {
                            descendants.add(r);
                        }
                    }

                    Set<MeterRegistry> removes = Collections.newSetFromMap(new IdentityHashMap<>());
                    removes.addAll(nonCompositeDescendants);
                    removes.removeAll(descendants);

                    Set<MeterRegistry> adds = Collections.newSetFromMap(new IdentityHashMap<>());
                    adds.addAll(descendants);
                    adds.removeAll(nonCompositeDescendants);

                    if (!removes.isEmpty() || !adds.isEmpty()) {
                        for (Meter meter : getMeters()) {
                            if (meter instanceof CompositeMeter) { // should always be
                                CompositeMeter composite = (CompositeMeter) meter;
                                removes.forEach(composite::remove);
                                adds.forEach(composite::add);
                            }
                        }
                    }

                    this.nonCompositeDescendants = descendants;
                    break;
                } finally {
                    registriesLock.set(false);
                }
            }
        }

        this.parents.forEach(p -> p.updateDescendants(true));
    }

    public Set<MeterRegistry> getRegistries() {
        return unmodifiableRegistries;
    }
}
