package org.springframework.metrics.spectator;

import org.springframework.metrics.Gauge;

public class SpectatorGauge implements Gauge {
    private com.netflix.spectator.api.Gauge gauge;

    public SpectatorGauge(com.netflix.spectator.api.Gauge gauge) {
        this.gauge = gauge;
    }

    @Override
    public void set(double value) {
        gauge.set(value);
    }

    @Override
    public double value() {
        return gauge.value();
    }
}
