/**
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.step;

import io.micrometer.core.instrument.Clock;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * A class similar to {@link StepDouble}, but records the maximum
 * value recorded in steps as opposed accumulated values.
 *
 * @author Samuel Cox
 * @author Johnny Lim
 */
class StepDoubleMax extends StepValue<Double> {
    private final AtomicLong current = new AtomicLong();

    public StepDoubleMax(Clock clock, long stepMillis) {
        super(clock, stepMillis);
    }

    @Override
    protected Supplier<Double> valueSupplier() {
        return () -> Double.longBitsToDouble(current.getAndSet(0));
    }

    @Override
    protected Double noValue() {
        return 0.0;
    }

    /**
     * Record a amount.
     */
    void record(double amount) {
        current.updateAndGet(current -> Math.max(current, Double.doubleToLongBits(amount)));
    }

}
