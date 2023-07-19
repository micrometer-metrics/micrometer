/*
 * Copyright 2017 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.composite;

import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.noop.NoopFunctionTimer;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

class CompositeFunctionTimer<T> extends AbstractCompositeMeter<FunctionTimer> implements FunctionTimer {

    private final WeakReference<T> ref;

    private final ToLongFunction<T> countFunction;

    private final ToDoubleFunction<T> totalTimeFunction;

    private final TimeUnit totalTimeFunctionUnit;

    CompositeFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction,
            TimeUnit totalTimeFunctionUnit) {
        super(id);
        this.ref = new WeakReference<>(obj);
        this.countFunction = countFunction;
        this.totalTimeFunction = totalTimeFunction;
        this.totalTimeFunctionUnit = totalTimeFunctionUnit;
    }

    @Override
    public double count() {
        return firstChild().count();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return firstChild().totalTime(unit);
    }

    @Override
    public TimeUnit baseTimeUnit() {
        return firstChild().baseTimeUnit();
    }

    @Override
    FunctionTimer newNoopMeter() {
        return new NoopFunctionTimer(getId());
    }

    @Override
    FunctionTimer registerNewMeter(MeterRegistry registry) {
        final T obj = ref.get();
        if (obj == null) {
            return null;
        }

        return FunctionTimer.builder(getId().getName(), obj, countFunction, totalTimeFunction, totalTimeFunctionUnit)
            .tags(getId().getTagsAsIterable())
            .description(getId().getDescription())
            .register(registry);
    }

}
