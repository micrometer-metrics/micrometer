/*
 * Copyright 2023 VMware, Inc.
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
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class StepHistogramTest {

    MockClock clock = new MockClock();

    Duration step = Duration.ofMinutes(1);

    DistributionStatisticConfig distributionStatisticConfig = getConfig(true);

    private static DistributionStatisticConfig getConfig(boolean percentilesHistogram) {
        return DistributionStatisticConfig.builder().percentilesHistogram(percentilesHistogram).build()
                .merge(DistributionStatisticConfig.DEFAULT);
    }

    @Test
    void aggregablePercentilesTrue_AddsBuckets() {
        boolean supportsAggregablePercentiles = true;
        try (StepBucketHistogram histogram = new StepBucketHistogram(clock, step.toMillis(),
                distributionStatisticConfig, supportsAggregablePercentiles)) {
            // TODO I don't think we should need this to have the correct buckets (even if
            // their counts are all 0)
            clock.add(step);
            assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts().length).isGreaterThan(0);
        }
    }

    @Test
    void aggregablePercentilesFalse_NoBuckets() {
        boolean supportsAggregablePercentiles = false;
        try (StepBucketHistogram histogram = new StepBucketHistogram(clock, step.toMillis(),
                distributionStatisticConfig, supportsAggregablePercentiles)) {
            // TODO I don't think we should need this to have the correct buckets (even if
            // their counts are all 0)
            clock.add(step);
            assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts().length).isZero();
        }
    }

    @Test
    void sloWithAggregablePercentilesFalse_onlySloBucket() {
        boolean supportsAggregablePercentiles = false;
        DistributionStatisticConfig config = DistributionStatisticConfig.builder().serviceLevelObjectives(4).build()
                .merge(distributionStatisticConfig);
        try (StepBucketHistogram histogram = new StepBucketHistogram(clock, step.toMillis(), config,
                supportsAggregablePercentiles)) {
            // TODO I don't think we should need this to have the correct buckets (even if
            // their counts are all 0)
            clock.add(step);
            assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts().length).isOne();
        }
    }

    @Test
    void sloWithAggregablePercentilesTrue_sloBucketPlusPercentilesHistogramBuckets() {
        boolean supportsAggregablePercentiles = true;
        // intentional SLO that is not in the percentile histogram buckets
        double slo = 15.0;
        DistributionStatisticConfig config = DistributionStatisticConfig.builder().serviceLevelObjectives(slo).build()
                .merge(distributionStatisticConfig);
        try (StepBucketHistogram histogram = new StepBucketHistogram(clock, step.toMillis(), config,
                supportsAggregablePercentiles)) {
            // TODO I don't think we should need this to have the correct buckets (even if
            // their counts are all 0)
            clock.add(step);
            assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts()).contains(new CountAtBucket(slo, 0));
        }
    }

    @Test
    void sloWithPercentileHistogramFalse_onlySloBucket() {
        boolean supportsAggregablePercentiles = true;
        // intentional SLO that is not in the percentile histogram buckets
        double slo = 15.0;
        DistributionStatisticConfig config = DistributionStatisticConfig.builder().serviceLevelObjectives(slo).build()
                .merge(getConfig(false));
        try (StepBucketHistogram histogram = new StepBucketHistogram(clock, step.toMillis(), config,
                supportsAggregablePercentiles)) {
            // TODO I don't think we should need this to have the correct buckets (even if
            // their counts are all 0)
            clock.add(step);
            assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts()).contains(new CountAtBucket(slo, 0));
        }
    }

    @Test
    void bucketCountRollover() {
        boolean supportsAggregablePercentiles = true;
        double slo = 15.0;
        DistributionStatisticConfig config = DistributionStatisticConfig.builder().serviceLevelObjectives(slo).build()
                .merge(distributionStatisticConfig);
        try (StepBucketHistogram histogram = new StepBucketHistogram(clock, step.toMillis(), config,
                supportsAggregablePercentiles)) {
            histogram.recordDouble(slo - 1);
            histogram.recordDouble(slo);
            histogram.recordDouble(slo + 1);
            assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts()).allMatch(bucket -> bucket.count() == 0);
            clock.add(step);
            Arrays.stream(histogram.takeSnapshot(0, 0, 0).histogramCounts()).forEach(bucket -> {
                if (Arrays.asList(slo, slo - 1, slo + 1).contains(bucket.bucket())) {
                    assertThat(bucket.count()).isOne();
                }
                else {
                    assertThat(bucket.count()).isZero();
                }
            });
            clock.add(step);
            assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts()).allMatch(bucket -> bucket.count() == 0);
        }
    }

    @Test
    void doesNotSupportPercentiles() {
        DistributionStatisticConfig config = DistributionStatisticConfig.builder().percentiles(0.5, 0.9).build().merge(distributionStatisticConfig);
        try (StepBucketHistogram histogram = new StepBucketHistogram(clock, step.toMillis(), config, false)) {
            histogram.recordDouble(10.0);
            clock.add(step);
            assertThat(histogram.takeSnapshot(0, 0, 0).percentileValues()).isEmpty();
        }
    }

}
