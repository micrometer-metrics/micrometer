package org.springframework.metrics;

import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.springframework.metrics.spectator.SpectatorMetricRegistry;
import org.springframework.metrics.tck.GaugeTest;
import org.springframework.metrics.tck.MetricsCompatibilityKit;
import org.springframework.metrics.tck.CounterTest;
import org.springframework.metrics.tck.TimerTest;

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
