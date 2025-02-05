/*
 * Copyright 2017 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.distribution;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MockClock;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeWindowFixedBoundaryHistogramTest {

    @Test
    void binarySearchForTail() {
        assertTailSearch(3, 1, 1.0, 5.0, 10.0);
        assertTailSearch(5, 1, 1.0, 5.0, 10.0);
        assertTailSearch(3, 1, 1.0, 4.0, 5.0, 10.0);
        assertTailSearch(3, 2, 1.0, 2.0, 5.0, 10.0);
        assertTailSearch(11, -1, 1.0, 5.0, 10.0);
    }

    private void assertTailSearch(int search, int expectedIndex, double... buckets) {
        DistributionStatisticConfig statisticConfig = DistributionStatisticConfig.builder()
            .serviceLevelObjectives(buckets)
            .build();
        try (TimeWindowFixedBoundaryHistogram histogram = new TimeWindowFixedBoundaryHistogram(Clock.SYSTEM,
                statisticConfig.merge(DistributionStatisticConfig.DEFAULT), false)) {
            FixedBoundaryHistogram bucket = histogram.newBucket();
            assertThat(bucket.leastLessThanOrEqualTo(search)).isEqualTo(expectedIndex);
        }
    }

    @Test
    void histogramsAreCumulativeByDefault() {
        try (TimeWindowFixedBoundaryHistogram histogram = new TimeWindowFixedBoundaryHistogram(new MockClock(),
                DistributionStatisticConfig.builder()
                    .serviceLevelObjectives(3.0, 6, 7)
                    .bufferLength(1)
                    .build()
                    .merge(DistributionStatisticConfig.DEFAULT),
                false)) {

            histogram.recordDouble(3);

            assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts()).containsExactly(new CountAtBucket(3.0, 1),
                    new CountAtBucket(6.0, 1), new CountAtBucket(7.0, 1));

            histogram.recordDouble(6);

            histogram.recordDouble(7);

            // Proves that the accumulated histogram is truly cumulative, and not just a
            // representation
            // of the last snapshot
            assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts()).containsExactly(new CountAtBucket(3.0, 1),
                    new CountAtBucket(6.0, 2), new CountAtBucket(7.0, 3));
        }
    }

    @Test
    void nonCumulativeHistogram() {
        try (TimeWindowFixedBoundaryHistogram histogram = new TimeWindowFixedBoundaryHistogram(new MockClock(),
                DistributionStatisticConfig.builder()
                    .serviceLevelObjectives(3.0, 6, 7)
                    .bufferLength(1)
                    .build()
                    .merge(DistributionStatisticConfig.DEFAULT),
                false, false)) {

            histogram.recordDouble(3);

            assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts()).containsExactly(new CountAtBucket(3.0, 1),
                    new CountAtBucket(6.0, 0), new CountAtBucket(7.0, 0));

            histogram.recordDouble(6);
            histogram.recordDouble(7);

            assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts()).containsExactly(new CountAtBucket(3.0, 1),
                    new CountAtBucket(6.0, 1), new CountAtBucket(7.0, 1));
        }
    }

    @Test
    void infinityBucketAdded() {
        try (TimeWindowFixedBoundaryHistogram histogram = new TimeWindowFixedBoundaryHistogram(new MockClock(),
                DistributionStatisticConfig.builder()
                    .serviceLevelObjectives(3.0, 6, 7)
                    .bufferLength(1)
                    .build()
                    .merge(DistributionStatisticConfig.DEFAULT),
                false, false, true)) {

            histogram.recordDouble(3);

            assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts()).containsExactly(new CountAtBucket(3.0, 1),
                    new CountAtBucket(6.0, 0), new CountAtBucket(7.0, 0),
                    new CountAtBucket(Double.POSITIVE_INFINITY, 0));

            histogram.recordDouble(6);
            histogram.recordDouble(7);
            histogram.recordDouble(9);

            assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts()).containsExactly(new CountAtBucket(3.0, 1),
                    new CountAtBucket(6.0, 1), new CountAtBucket(7.0, 1),
                    new CountAtBucket(Double.POSITIVE_INFINITY, 1));
        }
    }

    @Test
    void infinityBucketAddedWhenNoHistogramBucketsConfigured() {
        try (TimeWindowFixedBoundaryHistogram histogram = new TimeWindowFixedBoundaryHistogram(new MockClock(),
                DistributionStatisticConfig.DEFAULT, false, false, true)) {

            histogram.recordDouble(3);

            assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts())
                .containsExactly(new CountAtBucket(Double.POSITIVE_INFINITY, 1));

            histogram.recordDouble(6);
            histogram.recordDouble(7);
            histogram.recordDouble(9);

            assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts())
                .containsExactly(new CountAtBucket(Double.POSITIVE_INFINITY, 4));
        }
    }

}
