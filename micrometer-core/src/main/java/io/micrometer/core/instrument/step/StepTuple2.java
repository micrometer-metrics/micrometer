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

import io.micrometer.core.instrument.Clock;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Tracks multiple 'values' for periods (steps) of time. The previous step's values are
 * obtained by calling {@link #poll1()} or {@link #poll2()}.
 *
 * @author Jon Schneider
 * @since 1.5.1
 */
public class StepTuple2<T1, T2> {

    private final Clock clock;

    private final long stepMillis;

    private AtomicLong lastInitPos;

    private final T1 t1NoValue;

    private final T2 t2NoValue;

    private final Supplier<T1> t1Supplier;

    private final Supplier<T2> t2Supplier;

    private volatile T1 t1Previous;

    private volatile T2 t2Previous;

    public StepTuple2(Clock clock, long stepMillis, T1 t1NoValue, T2 t2NoValue, Supplier<T1> t1Supplier,
            Supplier<T2> t2Supplier) {
        this.clock = clock;
        this.stepMillis = stepMillis;
        this.t1NoValue = t1NoValue;
        this.t2NoValue = t2NoValue;
        this.t1Supplier = t1Supplier;
        this.t2Supplier = t2Supplier;
        this.t1Previous = t1NoValue;
        this.t2Previous = t2NoValue;
        lastInitPos = new AtomicLong(clock.wallTime() / stepMillis);
    }

    private void rollCount(long now) {
        long stepTime = now / stepMillis;
        long lastInit = lastInitPos.get();
        if (lastInit < stepTime && lastInitPos.compareAndSet(lastInit, stepTime)) {
            // Need to check if there was any activity during the previous step interval.
            // If there was then the init position will move forward by 1, otherwise it
            // will be older.
            // No activity means the previous interval should be set to the `init` value.
            t1Previous = (lastInit == stepTime - 1) ? t1Supplier.get() : t1NoValue;
            t2Previous = (lastInit == stepTime - 1) ? t2Supplier.get() : t2NoValue;
        }
    }

    /**
     * This is an internal method not meant for general use.
     * <p>
     * Rolls the values regardless of the clock or current time and ensures the value will
     * never roll over again after.
     * @since 1.11.0
     */
    protected void _closingRollover() {
        // ensure rollover does not happen again
        lastInitPos.set(Long.MAX_VALUE);
        t1Previous = t1Supplier.get();
        t2Previous = t2Supplier.get();
    }

    /**
     * @return The value for the last completed interval.
     */
    public T1 poll1() {
        rollCount(clock.wallTime());
        return t1Previous;
    }

    /**
     * @return The value for the last completed interval.
     */
    public T2 poll2() {
        rollCount(clock.wallTime());
        return t2Previous;
    }

}
