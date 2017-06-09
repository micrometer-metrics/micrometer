package org.springframework.metrics.export.datadog;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.impl.AtomicDouble;

import java.util.Collections;

class DatadogGauge implements Gauge {
    private final Id id;
    private final Clock clock;
    private final AtomicDouble value;

    /** Create a new instance. */
    DatadogGauge(Id id, Clock clock) {
        this.id = id;
        this.clock = clock;
        this.value = new AtomicDouble(0.0);
    }

    @Override public Id id() {
        return id;
    }

    @Override public boolean hasExpired() {
        return false;
    }

    @Override public Iterable<Measurement> measure() {
        final Measurement m = new Measurement(id, clock.wallTime(), value());
        return Collections.singletonList(m);
    }

    @Override public void set(double v) {
        value.set(v);
    }

    @Override public double value() {
        return value.get();
    }
}
