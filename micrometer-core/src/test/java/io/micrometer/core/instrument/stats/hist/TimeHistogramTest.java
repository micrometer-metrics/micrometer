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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TimeHistogramTest {
    @ParameterizedTest
    @EnumSource(Histogram.Summation.class)
    void linearTimeBuckets(Histogram.Summation summation) {
        TimeHistogram hist = Histogram.linearTime(TimeUnit.SECONDS, 1, 1, 3).create(summation);
        hist.bucketTimeScale(TimeUnit.MILLISECONDS);

        assertThat(hist.getBuckets().stream().map(Bucket::getTag))
            .containsExactly(1000.0, 2000.0, 3000.0, Double.POSITIVE_INFINITY);

        hist.observe(1000);

        assertThat(hist.getBuckets().stream().filter(b -> b.getTag() == 1000.0).findAny())
            .hasValueSatisfying(b -> assertThat(b.getValue()).isEqualTo(1));
    }

    @ParameterizedTest
    @EnumSource(Histogram.Summation.class)
    void exponentialTimeBuckets(Histogram.Summation summation) {
        TimeHistogram hist = Histogram.exponentialTime(TimeUnit.SECONDS, 1, 2, 3).create(summation);
        hist.bucketTimeScale(TimeUnit.MILLISECONDS);

        assertThat(hist.getBuckets().stream().map(Bucket::getTag))
            .containsExactly(1000.0, 2000.0, 4000.0, Double.POSITIVE_INFINITY);

        hist.observe(3000);

        assertThat(hist.getBuckets().stream().filter(b -> b.getTag() == 4000.0).findAny())
            .hasValueSatisfying(b -> assertThat(b.getValue()).isEqualTo(1));
    }

    @Test
    void filtersScaledToFunctionTimeUnits() {
        TimeHistogram hist = Histogram.linearTime(TimeUnit.SECONDS, 1, 2, 3).create(Histogram.Summation.Normal);
        hist.bucketTimeScale(TimeUnit.MILLISECONDS);

        // the clamp will be assumed to be in the bucket time scale (milliseconds in this case)
        hist.filterBuckets(BucketFilter.clampMax(1000.0));

        assertThat(hist.getBuckets().stream().map(Bucket::getTag)).containsExactly(1000.0);
    }
}
