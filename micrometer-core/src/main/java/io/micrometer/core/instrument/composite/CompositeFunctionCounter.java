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

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.noop.NoopFunctionCounter;

import java.lang.ref.WeakReference;
import java.util.function.ToDoubleFunction;

public class CompositeFunctionCounter<T> extends AbstractCompositeMeter<FunctionCounter> implements FunctionCounter {

    private final WeakReference<T> ref;

    private final ToDoubleFunction<T> f;

    CompositeFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        super(id);
        this.ref = new WeakReference<>(obj);
        this.f = f;
    }

    @Override
    public double count() {
        T value = ref.get();
        return value != null ? f.applyAsDouble(value) : 0;
    }

    @Override
    FunctionCounter newNoopMeter() {
        return new NoopFunctionCounter(getId());
    }

    @Override
    FunctionCounter registerNewMeter(MeterRegistry registry) {
        final T obj = ref.get();
        if (obj == null) {
            return null;
        }

        return FunctionCounter.builder(getId().getName(), obj, f)
            .tags(getId().getTagsAsIterable())
            .description(getId().getDescription())
            .baseUnit(getId().getBaseUnit())
            .register(registry);
    }

}
