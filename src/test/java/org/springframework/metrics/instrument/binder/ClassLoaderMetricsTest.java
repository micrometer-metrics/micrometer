package org.springframework.metrics.instrument.binder;

import org.junit.jupiter.api.Test;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.simple.SimpleMeterRegistry;

import static org.springframework.metrics.instrument.Assertions.assertGaugeValue;

class ClassLoaderMetricsTest {
    @Test
    void classLoadingMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry()
                .bind(new ClassLoaderMetrics());

        assertGaugeValue(registry, "classes_loaded", v -> v > 0);
    }
}
