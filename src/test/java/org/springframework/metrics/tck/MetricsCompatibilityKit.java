package org.springframework.metrics.tck;

import org.springframework.metrics.MetricRegistry;

public interface MetricsCompatibilityKit {
    MetricRegistry createRegistry();
}
