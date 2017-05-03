package org.springframework.metrics.instrument;

import java.util.concurrent.TimeUnit;

public class MockClock implements Clock {
    private long time = 0;

    @Override
    public long monotonicTime() {
        return time;
    }

    public static MockClock clock(MeterRegistry collector) {
        return (MockClock) collector.getClock();
    }

    public long addAndGet(long amount, TimeUnit unit) {
        time += unit.toNanos(amount);
        return time;
    }

    public long addAndGetNanos(long amount) {
        time += amount;
        return time;
    }
}
