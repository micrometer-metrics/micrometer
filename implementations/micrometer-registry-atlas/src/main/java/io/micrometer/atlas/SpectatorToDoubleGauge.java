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
package io.micrometer.atlas;

import com.netflix.spectator.api.*;
import io.micrometer.common.lang.Nullable;

import java.util.Collections;
import java.util.function.ToDoubleFunction;

/**
 * Gauge that is defined by executing a {@link ToDoubleFunction} on an object. This is
 * identical to com.netflix.spectator.api.ObjectGauge which is not accessible in
 * Spectator.
 */
class SpectatorToDoubleGauge<T> extends AbstractMeter<T> implements Gauge {

    private final ToDoubleFunction<T> f;

    SpectatorToDoubleGauge(Clock clock, Id id, @Nullable T obj, ToDoubleFunction<T> f) {
        super(clock, id, obj);
        this.f = f;
    }

    @Override
    public Iterable<Measurement> measure() {
        return Collections.singleton(new Measurement(id, clock.wallTime(), value()));
    }

    @Override
    public double value() {
        final T obj = ref.get();
        return (obj == null) ? Double.NaN : f.applyAsDouble(obj);
    }

}
