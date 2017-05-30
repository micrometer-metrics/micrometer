package org.springframework.metrics.instrument.stats;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class NormalHistogramTest {

    @Test
    void linear() {
        Histogram<Double> hist = NormalHistogram.linear(5, 10, 5);
        Arrays.asList(0, 14, 24, 30, 43, 1000).forEach(hist::observe);
        assertThat(hist.buckets()).containsExactlyInAnyOrder(5.0, 15.0, 25.0, 35.0, 45.0, Double.POSITIVE_INFINITY);
    }

    @Test
    void exponential() {
        Histogram<Double> hist = NormalHistogram.exponential(1, 2, 5);
        Arrays.asList(0d, 1.5, 3d, 7d, 16d, 17d).forEach(hist::observe);
        assertThat(hist.buckets()).containsExactly(1.0, 2.0, 4.0, 8.0, 16.0, Double.POSITIVE_INFINITY);
    }
}
