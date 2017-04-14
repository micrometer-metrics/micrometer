package org.springframework.metrics.tck;

import org.junit.Test;
import org.springframework.metrics.Gauge;
import org.springframework.metrics.MetricRegistry;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public abstract class GaugeTest implements MetricsCompatibilityKit {

    @Test
    public void numericValue() {
        MetricRegistry registry = createRegistry();
        AtomicInteger n = registry.gauge("myGauge", new AtomicInteger(0));
        n.set(1);

        Gauge g = (Gauge) registry.getMeters().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Expected a gauge to be registered"));

        assertEquals(1, g.value(), 1.0e-12);
    }
}
