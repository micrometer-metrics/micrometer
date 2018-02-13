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

import io.micrometer.core.instrument.MockClock;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeWindowHistogramTest {
    @Test
    void histogramsAreCumulative() {
        TimeWindowHistogram histogram = new TimeWindowHistogram(new MockClock(), DistributionStatisticConfig.DEFAULT);
        histogram.recordDouble(3);

        assertThat(histogram.histogramCountAtValue(3)).isEqualTo(1);

        histogram.recordDouble(6);

        // Proves that the accumulated histogram is truly accumulative, and not just a representation
        // of the last snapshot
        assertThat(histogram.histogramCountAtValue(3)).isEqualTo(1);

        assertThat(histogram.histogramCountAtValue(6)).isEqualTo(2);
        assertThat(histogram.histogramCountAtValue(7)).isEqualTo(2);
    }

    @Test
    void sampleValueAboveMaximumExpectedValue() {
        TimeWindowHistogram histogram = new TimeWindowHistogram(new MockClock(), DistributionStatisticConfig.builder()
            .maximumExpectedValue(2L)
            .build()
            .merge(DistributionStatisticConfig.DEFAULT));
        histogram.recordDouble(3);
        assertThat(histogram.histogramCountAtValue(3)).isEqualTo(1);
    }

    @Test
    void recordValuesThatExceedTheDynamicRange() {
        TimeWindowHistogram histogram = new TimeWindowHistogram(new MockClock(), DistributionStatisticConfig.DEFAULT);

        // If the dynamic range of the underlying recorder isn't pushed very far to the right, a small value will be handled normally.
        // Doing this primes the 1e-8 sample for failure
        histogram.recordDouble(1000000000);

        // This will be out of bounds for the underlying histogram
        histogram.recordDouble(1e-8);

        // Regardless of the imputed dynamic bound for the underlying histogram, Double.MAX_VALUE is always too large.
        histogram.recordDouble(Double.MAX_VALUE);
    }
}
