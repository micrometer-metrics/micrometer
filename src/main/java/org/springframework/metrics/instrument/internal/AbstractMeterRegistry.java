package org.springframework.metrics.instrument.internal;

import org.springframework.metrics.instrument.Clock;
import org.springframework.metrics.instrument.Meter;
import org.springframework.metrics.instrument.MeterRegistry;

import java.util.ArrayList;
import java.util.Collection;

public abstract class AbstractMeterRegistry implements MeterRegistry {
    private Collection<Meter> meters = new ArrayList<>();
    protected Clock clock;

    protected AbstractMeterRegistry(Clock clock) {
        this.clock = clock;
    }

    protected <T extends Meter> T register(T meter) {
        meters.add(meter);
        return meter;
    }

    @Override
    public Clock getClock() {
        return clock;
    }

    @Override
    public Collection<Meter> getMeters() {
        return meters;
    }
}
