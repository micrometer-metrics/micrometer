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

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.noop.NoopFunctionTimer;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

public class CompositeFunctionTimer<T> extends AbstractMeter implements FunctionTimer, CompositeMeter {
    private final WeakReference<T> ref;
    private final ToLongFunction<T> countFunction;
    private final ToDoubleFunction<T> totalTimeFunction;
    private final TimeUnit totalTimeFunctionUnits;

    private final Map<MeterRegistry, FunctionTimer> functionTimers = Collections.synchronizedMap(new LinkedHashMap<>());

    public CompositeFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnits) {
        super(id);
        this.ref = new WeakReference<>(obj);
        this.countFunction = countFunction;
        this.totalTimeFunction = totalTimeFunction;
        this.totalTimeFunctionUnits = totalTimeFunctionUnits;
    }

    @Override
    public void add(MeterRegistry registry) {
        T obj = ref.get();
        if(obj != null) {
            synchronized (functionTimers) {
                functionTimers.put(registry, registry.more().timer(getId(), obj, countFunction,
                    totalTimeFunction, totalTimeFunctionUnits));
            }
        }
    }

    @Override
    public void remove(MeterRegistry registry) {
        synchronized (functionTimers) {
            functionTimers.remove(registry);
        }
    }

    @Override
    public long count() {
        synchronized (functionTimers) {
            return functionTimers.values().stream().findFirst().orElse(NoopFunctionTimer.INSTANCE).count();
        }
    }

    @Override
    public double totalTime(TimeUnit unit) {
        synchronized (functionTimers) {
            return functionTimers.values().stream().findFirst().orElse(NoopFunctionTimer.INSTANCE).totalTime(unit);
        }
    }

    @Override
    public TimeUnit baseTimeUnit() {
        synchronized (functionTimers) {
            return functionTimers.values().stream().findFirst().orElse(NoopFunctionTimer.INSTANCE).baseTimeUnit();
        }
    }
}
