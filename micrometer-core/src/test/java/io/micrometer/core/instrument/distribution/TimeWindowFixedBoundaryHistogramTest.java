/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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
        assertTailSearch(3, 1, 1L, 5L, 10L);
        assertTailSearch(5, 1, 1L, 5L, 10L);
        assertTailSearch(3, 1, 1L, 4L, 5L, 10L);
        assertTailSearch(3, 2, 1L, 2L, 5L, 10L);
        assertTailSearch(11, -1, 1L, 5L, 10L);
    }

    private void assertTailSearch(int search, int expectedIndex, long... buckets) {
        TimeWindowFixedBoundaryHistogram.FixedBoundaryHistogram hist = new TimeWindowFixedBoundaryHistogram(Clock.SYSTEM,
                DistributionStatisticConfig.builder().sla(buckets).build()
                    .merge(DistributionStatisticConfig.DEFAULT), false).newBucket();
        assertThat(hist.leastLessThanOrEqualTo(search)).isEqualTo(expectedIndex);
    }

    @Test
    void histogramsAreCumulative() {
        TimeWindowFixedBoundaryHistogram histogram = new TimeWindowFixedBoundaryHistogram(new MockClock(),
                DistributionStatisticConfig.builder()
                        .sla(3, 6, 7)
                        .bufferLength(1)
                        .build()
                        .merge(DistributionStatisticConfig.DEFAULT), false);

        histogram.recordDouble(3);

        assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts()).contains(new CountAtBucket(3, 1));

        histogram.recordDouble(6);

        histogram.recordDouble(7);

        // Proves that the accumulated histogram is truly cumulative, and not just a representation
        // of the last snapshot
        assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts()).containsExactly(
                new CountAtBucket(3, 1),
                new CountAtBucket(6, 2),
                new CountAtBucket(7, 3)
        );
    }
}