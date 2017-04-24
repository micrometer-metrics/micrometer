package org.springframework.metrics.collector;

import java.util.ArrayList;
import java.util.Collection;

public abstract class AbstractMetricCollector implements MetricCollector {
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
