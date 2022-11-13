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
package io.micrometer.core.instrument.step;

import io.micrometer.core.instrument.Clock;

import java.util.concurrent.TimeUnit;

/**
 * A Clock that provides the capability to skew the underlying the clock by a specific
 * time. Calling {@link SkewableClock#setClockSkew(long, TimeUnit)} (long)} implies that
 * the clock will be skewed by the amount of time specified for any future use unless
 * modified again.
 *
 * @author Lenin Jaganathan
 */
public class SkewableClock implements Clock {

    private final Clock clock;

    private long skewedTimeInMillis;

    public SkewableClock(Clock clock) {
        this.clock = clock;
        this.skewedTimeInMillis = 0;
    }

    /**
     * Sets the clock skew to "amount" of time provided. Note: The skew set is relative to
     * underlying {@link SkewableClock#clock}.
     * @param amount - amount of time to be skew the original clock with
     * @param unit - {@link TimeUnit} of amount.
     */
    public void setClockSkew(long amount, TimeUnit unit) {
        skewedTimeInMillis = unit.toMillis(amount);
    }

    /**
     * Returns current clock skew time in milliseconds.
     */
    public long getClockSkew() {
        return this.skewedTimeInMillis;
    }

    public Clock getOriginalClock() {
        return this.clock;
    }

    @Override
    public long wallTime() {
        return clock.wallTime() + this.skewedTimeInMillis;
    }

    @Override
    public long monotonicTime() {
        return clock.monotonicTime();
    }

}
