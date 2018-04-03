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
        assertThat(hist.binarySearchTail(search)).isEqualTo(expectedIndex);
    }
}