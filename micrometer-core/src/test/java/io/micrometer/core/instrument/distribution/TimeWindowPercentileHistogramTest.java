/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument.distribution;

import io.micrometer.core.instrument.MockClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static io.micrometer.core.instrument.util.TimeUtils.millisToUnit;
import static io.micrometer.core.instrument.util.TimeUtils.secondsToUnit;
import static org.assertj.core.api.Assertions.assertThat;

class TimeWindowPercentileHistogramTest {

    MockClock clock = new MockClock();

    @Test
    void histogramsAreCumulative() {
        try (TimeWindowPercentileHistogram histogram = new TimeWindowPercentileHistogram(clock,
                DistributionStatisticConfig.builder()
                    .serviceLevelObjectives(3.0, 6, 7)
                    .build()
                    .merge(DistributionStatisticConfig.DEFAULT),
                false)) {

            histogram.recordDouble(3);

            assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts()).containsExactly(new CountAtBucket(3.0, 1),
                    new CountAtBucket(6.0, 1), new CountAtBucket(7.0, 1));

            histogram.recordDouble(6);

            // Proves that the accumulated histogram is truly cumulative, and not just a
            // representation of the last snapshot
            assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts()).containsExactly(new CountAtBucket(3.0, 1),
                    new CountAtBucket(6.0, 2), new CountAtBucket(7.0, 2));
        }
    }

    @Test
    void sampleValueAboveMaximumExpectedValue() {
        try (TimeWindowPercentileHistogram histogram = new TimeWindowPercentileHistogram(clock,
                DistributionStatisticConfig.builder()
                    .serviceLevelObjectives(3.0)
                    .maximumExpectedValue(2.0)
                    .build()
                    .merge(DistributionStatisticConfig.DEFAULT),
                false)) {

            histogram.recordDouble(3);
            assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts()).containsExactly(new CountAtBucket(3.0, 1));
        }
    }

    @Test
    void recordValuesThatExceedTheDynamicRange() {
        try (TimeWindowPercentileHistogram histogram = new TimeWindowPercentileHistogram(clock,
                DistributionStatisticConfig.builder()
                    .serviceLevelObjectives(Double.POSITIVE_INFINITY)
                    .build()
                    .merge(DistributionStatisticConfig.DEFAULT),
                false)) {

            // Regardless of the imputed dynamic bound for the underlying histogram,
            // Double.MAX_VALUE is always too
            // large.
            histogram.recordDouble(Double.MAX_VALUE);

            assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts())
                .containsExactly(new CountAtBucket(Double.POSITIVE_INFINITY, 0));
        }
    }

    @Test
    void percentiles() {
        try (TimeWindowPercentileHistogram histogram = new TimeWindowPercentileHistogram(clock,
                DistributionStatisticConfig.builder()
                    .percentiles(0.5, 0.9, 0.95)
                    .minimumExpectedValue(millisToUnit(1, TimeUnit.NANOSECONDS))
                    .maximumExpectedValue(secondsToUnit(30, TimeUnit.NANOSECONDS))
                    .build()
                    .merge(DistributionStatisticConfig.DEFAULT),
                false)) {

            for (long i = 1; i <= 10; i++) {
                histogram.recordLong((long) millisToUnit(i, TimeUnit.NANOSECONDS));
            }

            assertThat(histogram.takeSnapshot(0, 0, 0).percentileValues())
                .anyMatch(p -> percentileValueIsApproximately(p, 0.5, 5e6))
                .anyMatch(p -> percentileValueIsApproximately(p, 0.9, 9e6))
                .anyMatch(p -> percentileValueIsApproximately(p, 0.95, 10e6));
        }
    }

    @Test
    void percentilesWithNoSamples() {
        DistributionStatisticConfig config = DistributionStatisticConfig.builder()
            .percentiles(0.5)
            .build()
            .merge(DistributionStatisticConfig.DEFAULT);

        try (TimeWindowPercentileHistogram histogram = new TimeWindowPercentileHistogram(clock, config, false)) {

            ValueAtPercentile expectedPercentile = new ValueAtPercentile(0.5, 0);
            HistogramSnapshot snapshot = histogram.takeSnapshot(0, 0, 0);
            assertThat(snapshot.percentileValues()).containsExactly(expectedPercentile);
        }
    }

    @Test
    void percentilesChangeWithMoreRecentSamples() {
        DistributionStatisticConfig config = DistributionStatisticConfig.builder()
            .percentiles(0.5)
            .build()
            .merge(DistributionStatisticConfig.DEFAULT);

        try (TimeWindowPercentileHistogram histogram = new TimeWindowPercentileHistogram(clock, config, false)) {

            for (int i = 1; i <= 10; i++) {
                histogram.recordLong((long) millisToUnit(i, TimeUnit.NANOSECONDS));
            }

            // baseline median
            assertThat(histogram.takeSnapshot(0, 0, 0).percentileValues())
                .anyMatch(p -> percentileValueIsApproximately(p, 0.5, 5e6));

            for (int i = 11; i <= 20; i++) {
                histogram.recordLong((long) millisToUnit(i, TimeUnit.NANOSECONDS));
            }

            // median should have moved after seeing 10 more samples
            assertThat(histogram.takeSnapshot(0, 0, 0).percentileValues())
                .anyMatch(p -> percentileValueIsApproximately(p, 0.5, 10e6));
        }
    }

    private boolean percentileValueIsApproximately(ValueAtPercentile vp, double percentile, double nanos) {
        if (vp.percentile() != percentile)
            return false;
        double eps = Math.abs(1 - (vp.value() / nanos));
        return eps < 0.05;
    }

    @Test
    void timeBasedSlidingWindow() {
        final DistributionStatisticConfig config = DistributionStatisticConfig.builder()
            .percentiles(0.0, 0.5, 0.75, 0.9, 0.99, 0.999, 1.0)
            .expiry(Duration.ofSeconds(4))
            .bufferLength(4)
            .build()
            .merge(DistributionStatisticConfig.DEFAULT);

        // Start from 0 for more comprehensive timing calculation.
        clock.add(-1, TimeUnit.NANOSECONDS);
        assertThat(clock.wallTime()).isZero();

        Histogram histogram = new TimeWindowPercentileHistogram(clock, config, false);

        histogram.recordLong(10);
        histogram.recordLong(20);
        assertThat(percentileValue(histogram, 0.0)).isStrictlyBetween(9.0, 11.0);
        assertThat(percentileValue(histogram, 1.0)).isStrictlyBetween(19.0, 21.0);

        clock.add(900, TimeUnit.MILLISECONDS); // 900
        histogram.recordLong(30);
        histogram.recordLong(40);
        assertThat(percentileValue(histogram, 0.0)).isStrictlyBetween(9.0, 11.0);
        assertThat(percentileValue(histogram, 1.0)).isStrictlyBetween(38.0, 42.0);

        clock.add(99, TimeUnit.MILLISECONDS); // 999
        histogram.recordLong(9);
        histogram.recordLong(60);
        assertThat(percentileValue(histogram, 0.0)).isStrictlyBetween(8.0, 10.0);
        assertThat(percentileValue(histogram, 1.0)).isStrictlyBetween(58.0, 62.0);

        clock.add(1, TimeUnit.MILLISECONDS); // 1000
        histogram.recordLong(12);
        histogram.recordLong(70);
        assertThat(percentileValue(histogram, 0.0)).isStrictlyBetween(8.0, 10.0);
        assertThat(percentileValue(histogram, 1.0)).isStrictlyBetween(68.0, 72.0);

        clock.add(1001, TimeUnit.MILLISECONDS); // 2001
        histogram.recordLong(13);
        histogram.recordLong(80);
        assertThat(percentileValue(histogram, 0.0)).isStrictlyBetween(8.0, 10.0);
        assertThat(percentileValue(histogram, 1.0)).isStrictlyBetween(75.0, 85.0);

        clock.add(1000, TimeUnit.MILLISECONDS); // 3001
        assertThat(percentileValue(histogram, 0.0)).isStrictlyBetween(8.0, 10.0);
        assertThat(percentileValue(histogram, 1.0)).isStrictlyBetween(75.0, 85.0);

        clock.add(999, TimeUnit.MILLISECONDS); // 4000
        assertThat(percentileValue(histogram, 0.0)).isStrictlyBetween(11.0, 13.0);
        assertThat(percentileValue(histogram, 1.0)).isStrictlyBetween(75.0, 85.0);
        histogram.recordLong(1);
        histogram.recordLong(200);
        assertThat(percentileValue(histogram, 0.0)).isStrictlyBetween(0.0, 2.0);
        assertThat(percentileValue(histogram, 1.0)).isStrictlyBetween(190.0, 210.0);

        clock.add(10000, TimeUnit.MILLISECONDS); // 14000
        assertThat(percentileValue(histogram, 0.0)).isZero();
        assertThat(percentileValue(histogram, 1.0)).isZero();
        histogram.recordLong(3);

        clock.add(3999, TimeUnit.MILLISECONDS); // 17999
        assertThat(percentileValue(histogram, 0.0)).isStrictlyBetween(2.0, 4.0);
        assertThat(percentileValue(histogram, 1.0)).isStrictlyBetween(2.0, 4.0);

        clock.add(1, TimeUnit.MILLISECONDS); // 18000
        assertThat(percentileValue(histogram, 0.0)).isZero();
        assertThat(percentileValue(histogram, 1.0)).isZero();
    }

    private double percentileValue(Histogram histogram, double p) {
        for (ValueAtPercentile valueAtPercentile : histogram.takeSnapshot(0, 0, 0).percentileValues()) {
            if (valueAtPercentile.percentile() == p)
                return valueAtPercentile.value();
        }
        return Double.NaN;
    }

    @Test
    void nonCumulativeHistogram() {
        DistributionStatisticConfig config = DistributionStatisticConfig.builder()
            .serviceLevelObjectives(5, 10)
            .build()
            .merge(DistributionStatisticConfig.DEFAULT);
        Histogram histogram = new TimeWindowPercentileHistogram(clock, config, false, false, false);
        histogram.recordLong(3);
        histogram.recordLong(4);
        histogram.recordLong(10);

        assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts()).containsExactly(new CountAtBucket(5d, 2),
                new CountAtBucket(10d, 1));
    }

    @Test
    void infinityBucketAddedWhenHistogramIsPresent() {
        DistributionStatisticConfig config = DistributionStatisticConfig.builder()
            .serviceLevelObjectives(5, 10)
            .build()
            .merge(DistributionStatisticConfig.DEFAULT);
        Histogram histogram = new TimeWindowPercentileHistogram(clock, config, false, false, true);
        histogram.recordLong(3);
        histogram.recordLong(4);
        histogram.recordLong(11);

        assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts()).containsExactly(new CountAtBucket(5d, 2),
                new CountAtBucket(10d, 0), new CountAtBucket(Double.POSITIVE_INFINITY, 1));
    }

    @Test
    void infinityBucketAddedWhenNoHistogramBucketsAreConfigured() {
        DistributionStatisticConfig config = DistributionStatisticConfig.DEFAULT;
        Histogram histogram = new TimeWindowPercentileHistogram(clock, config, false, false, true);
        histogram.recordLong(3);
        histogram.recordLong(4);
        histogram.recordLong(11);

        assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts())
            .containsExactly(new CountAtBucket(Double.POSITIVE_INFINITY, 3));
    }

}
