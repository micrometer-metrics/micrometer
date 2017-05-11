package org.springframework.metrics.instrument.binder;

import org.junit.jupiter.api.Test;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.simple.SimpleMeterRegistry;

import static org.springframework.metrics.instrument.Assertions.assertGaugeValue;

class ProcessorMetricsTest {
    @Test
    void cpuMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry()
                .bind(new ProcessorMetrics());

        assertGaugeValue(registry, "cpu_total", v -> v > 0);
        assertGaugeValue(registry, "cpu_load_average", v -> v > 0);
    }
}
