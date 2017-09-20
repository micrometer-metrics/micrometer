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

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.micrometer.core.instrument.stats.hist.Histogram.Summation.Cumulative;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultHistogramTest {
    @ParameterizedTest
    @EnumSource(Histogram.Summation.class)
    void linearBuckets(Histogram.Summation summation) {
        DefaultHistogram<Double> hist = Histogram.linear(1, 1, 3).create(summation);
        assertThat(hist.getBuckets().stream().map(Bucket::getTag))
            .containsExactly(1.0, 2.0, 3.0, Double.POSITIVE_INFINITY);

        BucketFunction<Double> exp = Histogram.linearFunction(1, 1, 3);
        assertThat(exp.bucket(1)).isEqualTo(1);
        assertThat(exp.bucket(1.5)).isEqualTo(2);
        assertThat(exp.bucket(3)).isEqualTo(3);
        assertThat(exp.bucket(10)).isEqualTo(Double.POSITIVE_INFINITY);
    }

    @ParameterizedTest
    @EnumSource(Histogram.Summation.class)
    void exponentialBuckets(Histogram.Summation summation) {
        DefaultHistogram<Double> hist = Histogram.exponential(1, 2, 3).create(summation);
        assertThat(hist.getBuckets().stream().map(Bucket::getTag))
            .containsExactly(1.0, 2.0, 4.0, Double.POSITIVE_INFINITY);

        BucketFunction<Double> exp = Histogram.exponentialFunction(1, 2, 3);
        assertThat(exp.bucket(1)).isEqualTo(1);
        assertThat(exp.bucket(3)).isEqualTo(4);
        assertThat(exp.bucket(10)).isEqualTo(Double.POSITIVE_INFINITY);
    }

    private static class ProvidedHistogramFunctions implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
            return
                Stream.of(
                    Histogram.linear(1, 2, 3),
                    Histogram.linearTime(TimeUnit.NANOSECONDS, 1, 2, 3),
                    Histogram.exponential(1, 2, 3),
                    Histogram.exponentialTime(TimeUnit.NANOSECONDS, 1, 2, 3),
                    Histogram.percentiles(),
                    Histogram.percentilesTime()
                ).map(Arguments::of);
        }
    }

    @ParameterizedTest
    @ArgumentsSource(ProvidedHistogramFunctions.class)
    void cumulativeHistogramsContainPositiveInfinityBucket(Histogram.Builder<Double> builder) {
        Histogram<Double> hist = builder.create(Cumulative);
        hist.observe(1000);

        assertThat(hist.getBucket(Double.POSITIVE_INFINITY))
            .isNotNull()
            .satisfies(b -> assertThat(b.getValue()).isEqualTo(1));
    }

    @ParameterizedTest
    @ArgumentsSource(ProvidedHistogramFunctions.class)
    void valuesAboveClampedMaxStillAccumulatedToInfinityBucket(Histogram.Builder<Double> builder) {
        Histogram<Double> hist = builder.create(Cumulative);
        hist.filterBuckets(BucketFilter.clampMax(3.0));
        hist.observe(5);

        assertThat(hist.getBucket(Double.POSITIVE_INFINITY))
            .isNotNull()
            .satisfies(b -> assertThat(b.getValue()).isEqualTo(1));
    }
}
