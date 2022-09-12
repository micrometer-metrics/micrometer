/*
 * Copyright 2022 VMware, Inc.
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

package io.micrometer.signalfx;

import io.micrometer.core.instrument.distribution.CountAtBucket;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeltaHistogramCountsTest {

    @Test
    void empty() {
        DeltaHistogramCounts deltaHistogramCounts = new DeltaHistogramCounts();
        assertThat(deltaHistogramCounts.calculate(new CountAtBucket[] {})).isEmpty();
        assertThat(deltaHistogramCounts.calculate(new CountAtBucket[] {})).isEmpty();
    }

    @Test
    void nonEmpty() {
        DeltaHistogramCounts deltaHistogramCounts = new DeltaHistogramCounts();
        CountAtBucket[] first = new CountAtBucket[] { new CountAtBucket(1.0, 0), new CountAtBucket(5.0, 1),
                new CountAtBucket(Double.MAX_VALUE, 1) };
        assertThat(deltaHistogramCounts.calculate(first)).isEqualTo(first);
        CountAtBucket[] second = new CountAtBucket[] { new CountAtBucket(1.0, 0), new CountAtBucket(5.0, 2),
                new CountAtBucket(Double.MAX_VALUE, 3) };
        assertThat(deltaHistogramCounts.calculate(second)).isEqualTo(new CountAtBucket[] { new CountAtBucket(1.0, 0),
                new CountAtBucket(5.0, 1), new CountAtBucket(Double.MAX_VALUE, 2) });
    }

}
