package org.springframework.metrics.instrument;

public class MockClock implements Clock {
    public long time = 0;

    @Override
    public long monotonicTime() {
        return time;
    }

    public static MockClock clock(MeterRegistry collector) {
        return (MockClock) collector.getClock();
    }
}
