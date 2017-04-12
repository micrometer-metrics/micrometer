package org.springframework.metrics.prometheus;

import org.springframework.metrics.Gauge;

public class PrometheusGauge implements Gauge {
    private io.prometheus.client.Gauge.Child gauge;

    public PrometheusGauge(io.prometheus.client.Gauge.Child gauge) {
        this.gauge = gauge;
    }

    @Override
    public void set(double value) {
        gauge.set(value);
    }

    @Override
    public double value() {
        return gauge.get();
    }
}
