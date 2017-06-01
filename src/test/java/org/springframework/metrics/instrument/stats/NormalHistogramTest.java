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
