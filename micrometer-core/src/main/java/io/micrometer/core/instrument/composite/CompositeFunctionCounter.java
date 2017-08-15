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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.util.MeterId;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.function.ToDoubleFunction;

public class CompositeFunctionCounter<T> implements CompositeMeter, Counter {
    private final MeterId id;
    private final WeakReference<T> ref;
    private final ToDoubleFunction<T> f;

    public CompositeFunctionCounter(MeterId id, T obj, ToDoubleFunction<T> f) {
        this.id = id;
        this.ref = new WeakReference<>(obj);
        this.f = f;
    }

    @Override
    public void add(MeterRegistry registry) {
        T obj = ref.get();
        if(obj != null) {
            registry.counter(id.getName(), id.getTags(), obj, f);
        }
    }

    @Override
    public void remove(MeterRegistry registry) {
        // nothing to do
    }

    @Override
    public String getName() {
        return id.getName();
    }

    @Override
    public Iterable<Tag> getTags() {
        return id.getTags();
    }

    @Override
    public Type getType() {
        return Type.Counter;
    }

    @Override
    public void increment(double amount) {
        throw new UnsupportedOperationException("This counter tracks a cumulative value based on a " +
            "function rather than series of increments.");
    }

    @Override
    public double count() {
        T obj = ref.get();
        return obj == null ? 0 : f.applyAsDouble(obj);
    }

    @Override
    public List<Measurement> measure() {
        T obj = ref.get();
        return obj != null ? Collections.singletonList(id.measurement(count())) :
            Collections.emptyList();
    }
}
