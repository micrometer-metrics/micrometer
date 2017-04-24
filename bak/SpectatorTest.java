package org.springframework.metrics;

import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.springframework.metrics.collector.spectator.SpectatorMetricRegistry;
import org.springframework.metrics.collector.GaugeTest;
import org.springframework.metrics.collector.MetricsCompatibilityKit;
import org.springframework.metrics.collector.CounterTest;
import org.springframework.metrics.collector.TimerTest;

/**
 * Compatibility suite for spectator binding.
 */
@RunWith(Enclosed.class)
public interface SpectatorTest extends MetricsCompatibilityKit {
    default MetricRegistry createRegistry() {
        return new SpectatorMetricRegistry();
    }

    class Counter extends CounterTest implements SpectatorTest {}
    class Timer extends TimerTest implements SpectatorTest {}
    class Gauge extends GaugeTest implements SpectatorTest {}
}
