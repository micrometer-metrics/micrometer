package io.micrometer.core.instrument.histogram;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jon Schneider
 */
class HistogramConfigTest {
    @Test
    void merge() {
        HistogramConfig c1 = HistogramConfig.builder().percentiles(0.95).build();
        HistogramConfig c2 = HistogramConfig.builder().percentiles(0.90).build();

        HistogramConfig merged = c2.merge(c1).merge(HistogramConfig.DEFAULT);

        assertThat(merged.getPercentiles()).containsExactly(0.90);
        assertThat(merged.getHistogramExpiry()).isEqualTo(Duration.ofMinutes(2));
    }
}