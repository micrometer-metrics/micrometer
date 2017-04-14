package org.springframework.metrics;

import java.util.ArrayList;
import java.util.Collection;

public abstract class AbstractMetricRegistry implements MetricRegistry {
    private Collection<Meter> meters = new ArrayList<>();

    protected <T extends Meter> T register(T meter) {
        meters.add(meter);
        return meter;
    }

    @Override
    public Collection<Meter> getMeters() {
        return meters;
    }
}
