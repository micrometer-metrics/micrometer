/**
 * Copyright 2018 Pivotal Software, Inc.
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
package io.micrometer.core.instrument;

import io.micrometer.core.lang.Nullable;

import java.util.function.ToDoubleFunction;

/**
 * @param <T> The type of object from which the gauge's instantaneous value is determined.
 * @since 1.1.0
 */
class StrongReferenceGaugeFunction<T> implements ToDoubleFunction<T> {
    /**
     * Holding a reference to obj inside of this function effectively prevents it from being
     * garbage collected. Implementors of {@link Gauge} can then assume that they should hold
     * {@code obj} as a weak reference.
     * <p>
     * If obj is {@code null} initially then this gauge will not be reported.
     */
    @Nullable
    @SuppressWarnings("FieldCanBeLocal")
    private final T obj;

    private final ToDoubleFunction<T> f;

    StrongReferenceGaugeFunction(@Nullable T obj, ToDoubleFunction<T> f) {
        this.obj = obj;
        this.f = f;
    }

    @Override
    public double applyAsDouble(T value) {
        return f.applyAsDouble(value);
    }
}