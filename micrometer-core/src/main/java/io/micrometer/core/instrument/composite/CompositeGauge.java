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

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.noop.NoopGauge;

import java.lang.ref.WeakReference;
import java.util.function.ToDoubleFunction;

class CompositeGauge<T> extends AbstractCompositeMeter<Gauge> implements Gauge {

    private final WeakReference<T> ref;

    private final ToDoubleFunction<T> f;

    CompositeGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> f) {
        super(id);
        ref = new WeakReference<>(obj);
        this.f = f;
    }

    @Override
    public double value() {
        return firstChild().value();
    }

    @Override
    Gauge newNoopMeter() {
        return new NoopGauge(getId());
    }

    @Override
    Gauge registerNewMeter(MeterRegistry registry) {
        final T obj = ref.get();
        if (obj == null) {
            return null;
        }

        return Gauge.builder(getId().getName(), obj, f)
            .tags(getId().getTagsAsIterable())
            .description(getId().getDescription())
            .baseUnit(getId().getBaseUnit())
            .register(registry);
    }

}
