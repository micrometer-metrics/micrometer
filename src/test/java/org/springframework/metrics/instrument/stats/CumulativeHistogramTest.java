/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
