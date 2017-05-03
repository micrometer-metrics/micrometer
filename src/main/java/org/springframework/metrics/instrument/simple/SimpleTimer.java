package org.springframework.metrics.instrument.simple;

import org.springframework.metrics.instrument.Clock;
import org.springframework.metrics.instrument.internal.AbstractTimer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Jon Schneider
 */
public class SimpleTimer extends AbstractTimer {
    private AtomicLong count = new AtomicLong(0);
    private AtomicLong totalTime = new AtomicLong(0);

    public SimpleTimer() {
        this(Clock.SYSTEM);
    }

    public SimpleTimer(Clock clock) {
        super(clock);
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        count.incrementAndGet();
        totalTime.addAndGet(TimeUnit.NANOSECONDS.convert(amount, unit));
    }

    @Override
    public long count() {
        return count.get();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return nanosToUnit(totalTime.get(), unit);
    }
}
