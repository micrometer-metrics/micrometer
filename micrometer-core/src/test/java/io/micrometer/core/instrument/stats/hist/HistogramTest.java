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
package io.micrometer.core.instrument.stats.hist;

import io.micrometer.core.Issue;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class HistogramTest {

    @ParameterizedTest
    @EnumSource(Histogram.Summation.class)
    @Issue("#120")
    void useFiltersToClampBucketDomains(Histogram.Summation summation) {
        Histogram<Double> histogram = Histogram.linear(1, 1, 10)
            .create(summation)
            .filterBuckets(BucketFilter.clampMax(8.0))
            .filterBuckets(BucketFilter.clampMin(5.0));

        IntStream.range(0, 11).forEach(histogram::observe);

        assertThat(histogram.getBuckets().stream().map(Bucket::getTag))
            .contains(5.0, 6.0, 7.0, 8.0)
            .doesNotContain(1.0, 2.0, 3.0, 4.0, 9.0, 10.0);
    }

    @Test
    void cumulativeSummation() {
        Histogram<Double> hist = Histogram.linear(1, 1, 3).create(Histogram.Summation.Cumulative);
        hist.observe(1);
        hist.observe(1);

        assertThat(hist.getBucket(1.0).getValue()).isEqualTo(2);
        assertThat(hist.getBucket(2.0).getValue()).isEqualTo(2);
    }

    @Test
    void nonCumulativeSummation() {
        Histogram<Double> hist = Histogram.linear(1, 1, 3).create(Histogram.Summation.Normal);
        hist.observe(1);
        hist.observe(1);

        assertThat(hist.getBucket(1.0).getValue()).isEqualTo(2);
        assertThat(hist.getBucket(2.0).getValue()).isEqualTo(0);
    }
}
