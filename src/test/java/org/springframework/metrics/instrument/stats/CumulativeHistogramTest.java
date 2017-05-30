package org.springframework.metrics.instrument.stats;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CumulativeHistogramTest {

    @Test
    void linear() {
        Histogram<Double> hist = CumulativeHistogram.linear(5, 10, 5);
        assertThat(hist.buckets()).containsExactly(5.0, 15.0, 25.0, 35.0, 45.0, Double.POSITIVE_INFINITY);
    }

    @Test
    void exponential() {
        Histogram<Double> hist = CumulativeHistogram.exponential(1, 2, 5);
        assertThat(hist.buckets()).containsExactly(1.0, 2.0, 4.0, 8.0, 16.0, Double.POSITIVE_INFINITY);
    }
}
