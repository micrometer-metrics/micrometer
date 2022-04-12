package io.micrometer.registry.otlp;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.cumulative.CumulativeCounter;

import java.util.concurrent.TimeUnit;

class StartTimeAwareCumulativeCounter extends CumulativeCounter implements StartTimeAwareMeter {
    final long startTimeNanos;

    StartTimeAwareCumulativeCounter(Id id, Clock clock) {
        super(id);
        this.startTimeNanos = TimeUnit.MILLISECONDS.toNanos(clock.wallTime());
    }

    public long getStartTimeNanos() {
        return this.startTimeNanos;
    }
}
