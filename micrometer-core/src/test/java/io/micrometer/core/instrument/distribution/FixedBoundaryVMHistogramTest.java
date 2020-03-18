/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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

class FixedBoundaryVMHistogramTest {
    @Test
    void checkUpperBoundLookup() {
        try (FixedBoundaryVMHistogram histogram = new FixedBoundaryVMHistogram()) {
            assertThat(histogram.getVMRangeValue(0.0d)).isEqualTo("0...0");
            assertThat(histogram.getVMRangeValue(1e-9d)).isEqualTo("0...1.0e-9");
            assertThat(histogram.getVMRangeValue(Double.POSITIVE_INFINITY)).isEqualTo("1.0e18...+Inf");
            assertThat(histogram.getVMRangeValue(1e18d)).isEqualTo("9.5e17...1.0e18");
        }
    }
}
