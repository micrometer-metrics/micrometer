package org.springframework.metrics.collector.spectator;

import com.netflix.spectator.api.Registry;
import org.springframework.metrics.collector.Gauge;

public class SpectatorGauge implements Gauge {
    private com.netflix.spectator.api.Gauge gauge;

    public SpectatorGauge(com.netflix.spectator.api.Gauge gauge) {
        this.gauge = gauge;
    }

    @Override
    public double value() {
        return gauge.value();
    }
}
