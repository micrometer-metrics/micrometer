package org.springframework.metrics.export.datadog;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.impl.StepLong;

import java.util.Collections;

/**
 * Counter that reports a rate per second to Datadog. Note that {@link #count()} will
 * report the number events in the last complete interval rather than the total for
 * the life of the process.
 */
public class DatadogCounter implements Counter {
    private final Id id;
    private final StepLong value;

    /** Create a new instance. */
    DatadogCounter(Id id, Clock clock, long step) {
        this.id = id;
        this.value = new StepLong(0L, clock, step);
    }

    @Override public Id id() {
        return id;
    }

    @Override public boolean hasExpired() {
        return false;
    }

    @Override public Iterable<Measurement> measure() {
        final double rate = value.pollAsRate();
        final Measurement m = new Measurement(id, value.timestamp(), rate);
        return Collections.singletonList(m);
    }

    @Override public void increment() {
        value.getCurrent().incrementAndGet();
    }

    @Override public void increment(long amount) {
        value.getCurrent().addAndGet(amount);
    }

    @Override public long count() {
        return value.poll();
    }
}
