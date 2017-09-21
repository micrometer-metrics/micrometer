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
