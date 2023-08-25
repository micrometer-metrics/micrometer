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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.noop.NoopTimeGauge;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

class CompositeTimeGauge<T> extends AbstractCompositeMeter<TimeGauge> implements TimeGauge {

    private final WeakReference<T> ref;

    private final ToDoubleFunction<T> f;

    private final TimeUnit fUnit;

    CompositeTimeGauge(Id id, @Nullable T obj, TimeUnit fUnit, ToDoubleFunction<T> f) {
        super(id);
        ref = new WeakReference<>(obj);
        this.f = f;
        this.fUnit = fUnit;
    }

    @Override
    public double value() {
        return firstChild().value();
    }

    @Override
    public TimeUnit baseTimeUnit() {
        return firstChild().baseTimeUnit();
    }

    @Override
    TimeGauge newNoopMeter() {
        return new NoopTimeGauge(getId());
    }

    @Override
    TimeGauge registerNewMeter(MeterRegistry registry) {
        final T obj = ref.get();
        if (obj == null) {
            return null;
        }

        return TimeGauge.builder(getId().getName(), obj, fUnit, f)
            .tags(getId().getTagsAsIterable())
            .description(getId().getDescription())
            .register(registry);
    }

}
