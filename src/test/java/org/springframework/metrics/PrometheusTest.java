package org.springframework.metrics;

import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.springframework.metrics.prometheus.PrometheusMetricRegistry;
import org.springframework.metrics.tck.CounterTest;
import org.springframework.metrics.tck.MetricsCompatibilityKit;
import org.springframework.metrics.tck.TimerTest;

/**
 * Compatibility suite for Prometheus binding.
 */
@RunWith(Enclosed.class)
public interface PrometheusTest extends MetricsCompatibilityKit {
    default MetricRegistry createRegistry() {
        return new PrometheusMetricRegistry();
    }

    class Counter extends CounterTest implements PrometheusTest {}
    class Timer extends TimerTest implements PrometheusTest {}
}
