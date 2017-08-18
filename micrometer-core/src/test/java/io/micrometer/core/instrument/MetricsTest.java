package io.micrometer.core.instrument;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsTest {
    @Test
    void staticMetricsAreInitiallyNoop() {
        // doesn't blow up
        Metrics.counter("counter").increment();
    }

    @Test
    void metricCanBeCreatedBeforeStaticRegistryIsConfigured() {
        // doesn't blow up
        Counter counter = Metrics.counter("counter");
        counter.increment();

        Metrics.addRegistry(new SimpleMeterRegistry());
        counter.increment();

        assertThat(Metrics.globalRegistry.find("counter").counter())
            .hasValueSatisfying(c -> assertThat(c.count()).isEqualTo(1));
    }
}
