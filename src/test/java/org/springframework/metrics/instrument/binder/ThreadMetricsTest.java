package org.springframework.metrics.instrument.binder;

import org.junit.jupiter.api.Test;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.simple.SimpleMeterRegistry;

import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.metrics.instrument.Assertions.assertGaugeValue;

class ThreadMetricsTest {
    @Test
    void threadMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry()
                .bind(new ThreadMetrics());

        assertGaugeValue(registry, "threads_live", v -> v > 0);
        assertGaugeValue(registry, "threads_daemon", v -> v > 0);

        AtomicInteger peak = new AtomicInteger(0);
        assertGaugeValue(registry, "threads_peak", v -> {
            peak.set(v.intValue());
            return v > 0;
        });

        // should bump the peak by one
        Executors.newSingleThreadExecutor().submit(() -> {
            // do nothing
        });

        assertGaugeValue(registry, "threads_peak", v -> v > peak.get());
    }
}
