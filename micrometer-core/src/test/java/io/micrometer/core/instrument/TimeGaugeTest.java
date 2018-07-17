package io.micrometer.core.instrument;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TimeGaugeTest {
    @Test
    void hasBaseTimeUnit() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AtomicLong n = new AtomicLong(0);
        TimeGauge g = registry.more().timeGauge("my.time.gauge", Tags.empty(), n, TimeUnit.SECONDS, AtomicLong::doubleValue);

        assertThat(g.getId().getBaseUnit()).isEqualTo("seconds");
    }
}