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
     * @return current clock skew time in milliseconds.
     */
    public long getClockSkew() {
        return this.skewedTimeInMillis;
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
