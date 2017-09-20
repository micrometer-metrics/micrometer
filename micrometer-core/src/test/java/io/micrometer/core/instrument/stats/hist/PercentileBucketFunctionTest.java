package io.micrometer.core.instrument.stats.hist;

import io.micrometer.core.Issue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PercentileBucketFunctionTest {
    @Issue("#127")
    @Test
    void percentileSampleOnBucketBoundary() {
        Histogram<Double> hist = Histogram.percentiles().create(Histogram.Summation.Cumulative);
        hist.observe(0.0);
        hist.observe(1.0); // values less than 4 receive special treatment for performance
        hist.observe(85.0);

        assertThat(hist.getBucket(1.0))
            .isNotNull()
            .satisfies(b -> assertThat(b.getValue()).isEqualTo(2));

        assertThat(hist.getBucket(85.0))
            .isNotNull()
            .satisfies(b -> assertThat(b.getValue()).isEqualTo(3));
    }
}
