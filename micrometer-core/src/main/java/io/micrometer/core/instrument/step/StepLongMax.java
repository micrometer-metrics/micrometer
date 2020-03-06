/**
 * Copyright 2020 Pivotal Software, Inc.
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

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Clock;

/**
 * A class similar to {@link StepLong}, but records the maximum <b>positive</b>
 * value recorded in steps as opposed accumulated values.
 */
class StepLongMax extends StepValue<Long> {
    private final AtomicLong current = new AtomicLong(0);

    public StepLongMax(Clock clock, long stepMillis) {
        super(clock, stepMillis);
    }

    @Override
    protected Supplier<Long> valueSupplier() {
        return () -> current.getAndSet(0L);
    }

    @Override
    protected Long noValue() {
        return 0L;
    }

    /**
     * Record a positive amount.
     *
     * @throws {@link IllegalArgumentException} if the amount is negative.
     */
    void record(final long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Only positive values can be recorded.");
        }
        current.updateAndGet(curr -> Math.max(curr, amount));
    }

}
