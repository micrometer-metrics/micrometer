/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.internal;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.util.MeterEquivalence;
import io.micrometer.core.lang.Nullable;
import io.micrometer.core.util.internal.logging.WarnThenDebugLogger;

import java.lang.ref.WeakReference;
import java.util.function.ToDoubleFunction;

/**
 * Default implementation for {@link Gauge}.
 *
 * @param <T> reference type
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public class DefaultGauge<T> extends AbstractMeter implements Gauge {

    private static final WarnThenDebugLogger logger = new WarnThenDebugLogger(DefaultGauge.class);

    private final WeakReference<T> ref;
    private final ToDoubleFunction<T> value;

    public DefaultGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> value) {
        super(id);
        this.ref = new WeakReference<>(obj);
        this.value = value;
    }

    @Override
    public double value() {
        T obj = ref.get();
        if (obj != null) {
            try {
                return value.applyAsDouble(obj);
            }
            catch (Throwable ex) {
                logger.log("Failed to apply the value function for the gauge '" + getId().getName() + "'.", ex);
            }
        }
        return Double.NaN;
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
