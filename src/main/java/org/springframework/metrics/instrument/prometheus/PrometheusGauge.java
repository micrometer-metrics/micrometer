package org.springframework.metrics.instrument.prometheus;

import org.springframework.metrics.instrument.Gauge;

public class PrometheusGauge implements Gauge {
    private io.prometheus.client.Gauge.Child gauge;

    public PrometheusGauge(io.prometheus.client.Gauge.Child gauge) {
        this.gauge = gauge;
    }

    @Override
    public double value() {
        return gauge.get();
    }
}
