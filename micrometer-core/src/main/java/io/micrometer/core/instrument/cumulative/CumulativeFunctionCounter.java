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
package io.micrometer.core.instrument.cumulative;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Meter;

import java.lang.ref.WeakReference;
import java.util.function.ToDoubleFunction;

public class CumulativeFunctionCounter<T> extends AbstractMeter implements FunctionCounter {

    private final WeakReference<T> ref;

    private final ToDoubleFunction<T> f;

    private volatile double last;

    public CumulativeFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        super(id);
        this.ref = new WeakReference<>(obj);
        this.f = f;
    }

    @Override
    public double count() {
        T obj2 = ref.get();
        return obj2 != null ? (last = f.applyAsDouble(obj2)) : last;
    }

}
