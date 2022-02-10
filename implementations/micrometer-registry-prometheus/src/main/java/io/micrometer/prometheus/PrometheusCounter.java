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
package io.micrometer.prometheus;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.util.MeterEquivalence;
import io.prometheus.client.exemplars.CounterExemplarSampler;
import io.prometheus.client.exemplars.Exemplar;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.DoubleAdder;

import io.micrometer.core.lang.NonNull;
import io.micrometer.core.lang.Nullable;

public class PrometheusCounter extends AbstractMeter implements Counter {
    private DoubleAdder count = new DoubleAdder();
    private final AtomicReference<Exemplar> exemplar = new AtomicReference<>();
    @Nullable
    private final CounterExemplarSampler exemplarSampler;

    // Allow to get back examplar for Sample creation on counter creation
    Exemplar exemplar() {
        return exemplar.get();
    }

    PrometheusCounter(Meter.Id id) {
        this(id, null);
    }

    PrometheusCounter(Meter.Id id, @Nullable CounterExemplarSampler exemplarSampler) {
        super(id);
        this.exemplarSampler = exemplarSampler;
    }

    @Override
    public void increment(double amount) {
        if (amount > 0)
            count.add(amount);
        if (exemplarSampler != null) {
            updateExemplar(amount, exemplarSampler);
        }
    }

    /*
     * Ask for sample to eventually update the current exemplar for counter The
     * sampler used is the standard one from micrometer that apply a time windows
     * around 7 secondes for change
     */
    private void updateExemplar(double amount, @NonNull CounterExemplarSampler exemplarSampler) {
        Exemplar prev;
        Exemplar next;
        do {
            prev = exemplar.get();
            next = exemplarSampler.sample(amount, prev);
            if (next == null || next == prev) {
                return;
            }
        } while (!exemplar.compareAndSet(prev, next));
    }

    @Override
    public double count() {
        return count.doubleValue();
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
