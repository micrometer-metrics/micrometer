package org.springframework.metrics.collector.prometheus;

import org.springframework.metrics.collector.Gauge;

public class PrometheusGauge implements Gauge {
    private PrometheusToDoubleGuage<?>.Child gauge;

    public PrometheusGauge(PrometheusToDoubleGuage<?>.Child gauge) {
        this.gauge = gauge;
    }

    @Override
    public double value() {
        return gauge.value();
    }
}
