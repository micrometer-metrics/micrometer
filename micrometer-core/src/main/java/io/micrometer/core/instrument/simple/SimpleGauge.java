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
package io.micrometer.core.instrument.simple;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.util.MeterEquivalence;

import java.lang.ref.WeakReference;
import java.util.function.ToDoubleFunction;

public class SimpleGauge<T> extends AbstractMeter implements Gauge {
    private final WeakReference<T> ref;
    private final ToDoubleFunction<T> value;

    public SimpleGauge(Meter.Id id, T obj, ToDoubleFunction<T> value) {
        super(id);
        this.ref = new WeakReference<>(obj);
        this.value = value;
    }

    @Override
    public double value() {
        return value.applyAsDouble(ref.get());
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return MeterEquivalence.equals(this, o);
    }

    @Override
    public int hashCode() {
        return MeterEquivalence.hashCode(this);
    }
}
