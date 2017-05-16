package org.springframework.metrics.instrument.binder;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.metrics.instrument.Counter;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class LogbackMetricsTest {
    @Test
    void logbackLevelMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry()
                .bind(new LogbackMetrics());

        assertLogCounter(registry, 0);

        Logger logger = LoggerFactory.getLogger("foo");
        logger.warn("warn");
        logger.error("error");

        assertLogCounter(registry, 2);
    }

    private void assertLogCounter(MeterRegistry registry, int n) {
        assertThat(registry.find("logback_events").stream()
                .map(c -> ((Counter) c).count())
                .reduce(Double::sum)
                .get()).isEqualTo(n, offset(1.0e-12));
    }
}
