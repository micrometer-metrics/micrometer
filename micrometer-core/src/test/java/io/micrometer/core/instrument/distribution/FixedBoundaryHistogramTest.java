package io.micrometer.core.instrument.distribution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FixedBoundaryHistogramTest {

    private static final double[] BUCKET_BOUNDS = new double[] { 1, 10, 100 };

    private FixedBoundaryHistogram fixedBoundaryHistogram;

    @BeforeEach
    void setup() {
        fixedBoundaryHistogram = new FixedBoundaryHistogram(BUCKET_BOUNDS, false);
    }

    @Test
    void testGetBuckets() {
        assertThat(fixedBoundaryHistogram.getBuckets()).containsExactly(BUCKET_BOUNDS);
    }

    @ParameterizedTest
    @MethodSource("valuedIndexProvider")
    void testLeastLessThanOrEqualTo(long value, int expectedIndex) {
        assertThat(fixedBoundaryHistogram.leastLessThanOrEqualTo(value)).isEqualTo(expectedIndex);
    }

    private static Stream<Arguments> valuedIndexProvider() {
        return Stream.of(Arguments.of(0, 0), Arguments.of(1, 0), Arguments.of(2, 1), Arguments.of(5, 1),
                Arguments.of(10, 1), Arguments.of(11, 2), Arguments.of(90, 2), Arguments.of(100, 2),
                Arguments.of(101, -1), Arguments.of(Long.MAX_VALUE, -1));
    }

    @Test
    void testReset() {
        fixedBoundaryHistogram.record(1);
        fixedBoundaryHistogram.record(10);
        fixedBoundaryHistogram.record(100);
        assertThat(fixedBoundaryHistogram.getCountsAtBucket()).allMatch(countAtBucket -> countAtBucket.count() == 1);
        fixedBoundaryHistogram.reset();
        assertThat(fixedBoundaryHistogram.getCountsAtBucket()).allMatch(countAtBucket -> countAtBucket.count() == 0);
    }

    @Test
    void testCountsAtBucket() {
        fixedBoundaryHistogram.record(1);
        fixedBoundaryHistogram.record(10);
        fixedBoundaryHistogram.record(100);
        assertThat(fixedBoundaryHistogram.getCountsAtBucket()).allMatch(countAtBucket -> countAtBucket.count() == 1);
        fixedBoundaryHistogram.reset();
        assertThat(fixedBoundaryHistogram.getCountsAtBucket()).allMatch(countAtBucket -> countAtBucket.count() == 0);
        fixedBoundaryHistogram.record(0);
        assertThat(fixedBoundaryHistogram.getCountsAtBucket()).containsExactly(new CountAtBucket(1.0, 1),
                new CountAtBucket(10.0, 0), new CountAtBucket(100.0, 0));
    }

    @Test
    void testCumulativeCounts() {
        fixedBoundaryHistogram = new FixedBoundaryHistogram(BUCKET_BOUNDS, true);
        fixedBoundaryHistogram.record(1);
        fixedBoundaryHistogram.record(10);
        fixedBoundaryHistogram.record(100);
        assertThat(fixedBoundaryHistogram.getCountsAtBucket()).containsExactly(new CountAtBucket(1.0, 1),
                new CountAtBucket(10.0, 2), new CountAtBucket(100.0, 3));
    }

}
