package org.springframework.metrics.instrument.spectator;

import org.springframework.metrics.instrument.Gauge;

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
