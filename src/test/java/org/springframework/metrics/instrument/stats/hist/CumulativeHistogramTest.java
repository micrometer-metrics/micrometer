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
package org.springframework.metrics.instrument.stats.hist;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.offset;
import static org.springframework.metrics.instrument.stats.hist.CumulativeHistogram.*;

class CumulativeHistogramTest {

    @Test
    void linearBuckets() {
        Histogram<Double> hist = buckets(linear(5, 10, 5));
        assertThat(hist.getBuckets().stream().map(b -> b.getTag(Object::toString)))
                .containsExactlyInAnyOrder("5.0", "15.0", "25.0", "35.0", "45.0", "Infinity");
    }

    @Test
    void exponentialBuckets() {
        Histogram<Double> hist = buckets(exponential(1, 2, 5));
        assertThat(hist.getBuckets().stream().map(b -> b.getTag(Object::toString)))
                .containsExactly("1.0", "2.0", "4.0", "8.0", "16.0", "Infinity");
    }

    @Test
    void shiftTimeScales() {
        TimeScaleCumulativeHistogram hist = buckets(linear(0, 10, 10), TimeUnit.MILLISECONDS);

        // suppose the registry implementation knows its monitoring backend requires seconds as the base unit of time
        TimeScaleCumulativeHistogram shifted = hist.shiftScale(TimeUnit.SECONDS);

        // then it is assumed that the summary or timer controlling this histogram will also send observations to it in seconds
        shifted.observe(0.075);

        assertThat(shifted.getBuckets().stream())
                .anySatisfy(b -> {
                    assertThat(b.getTag()).isEqualTo("0.08");
                    assertThat(b.getValue()).isEqualTo(1, offset(1e-12));
                })
                .anySatisfy(b -> {
                    assertThat(b.getTag()).isEqualTo("0.09");
                    assertThat(b.getValue()).isEqualTo(1, offset(1e-12));
                });
    }
}
