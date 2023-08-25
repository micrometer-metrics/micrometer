/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.step;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Clock;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Tracks 'values' for periods (steps) of time. The previous step's value is obtained by
 * calling {@link #poll}.
 *
 * @author Jon Schneider
 * @author Samuel Cox
 * @since 1.4.0
 */
public abstract class StepValue<V> {

    private final Clock clock;

    private final long stepMillis;

    private final AtomicLong lastInitPos;

    private volatile V previous;

    public StepValue(final Clock clock, final long stepMillis) {
        this(clock, stepMillis, null);
    }

    /**
     * Creates a {@code StepValue} instance.
     * @param clock clock
     * @param stepMillis step in milliseconds
     * @param initValue initial value
     * @since 1.11.0
     */
    protected StepValue(final Clock clock, final long stepMillis, @Nullable final V initValue) {
        this.clock = clock;
        this.stepMillis = stepMillis;
        this.previous = initValue == null ? noValue() : initValue;
        lastInitPos = new AtomicLong(clock.wallTime() / stepMillis);
    }

    protected abstract Supplier<V> valueSupplier();

    /**
     * @return value that should be returned by {@link #poll} if within the first step
     * period or if the previous step didn't record a value.
     */
    protected abstract V noValue();

    private void rollCount(long now) {
        final long stepTime = now / stepMillis;
        final long lastInit = lastInitPos.get();
        if (lastInit < stepTime && lastInitPos.compareAndSet(lastInit, stepTime)) {
            final V v = valueSupplier().get();
            // Need to check if there was any activity during the previous step interval.
            // If there was then the init position will move forward by 1, otherwise it
            // will be older.
            // No activity means the previous interval should be set to the `init` value.
            previous = (lastInit == stepTime - 1) ? v : noValue();
        }
    }

    /**
     * @return The value for the last completed interval.
     */
    public V poll() {
        rollCount(clock.wallTime());
        return previous;
    }

    /**
     * This is an internal method not meant for general use.
     * <p>
     * Rolls the values regardless of the clock or current time and ensures the value will
     * never roll over again after.
     * @since 1.11.0
     */
    protected void _closingRollover() {
        // make sure value does not roll over again if passing a step boundary
        lastInitPos.set(Long.MAX_VALUE);
        previous = valueSupplier().get();
    }

}
