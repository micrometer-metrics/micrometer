/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.stackdriver;

import com.google.api.Distribution;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StackdriverMeterRegistry}
 */
class StackdriverMeterRegistryTest {

    MockClock clock = new MockClock();

    StackdriverConfig config = new StackdriverConfig() {
        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public String projectId() {
            return "doesnotmatter";
        }

        @Override
        public @Nullable String get(String key) {
            return null;
        }
    };

    StackdriverMeterRegistry meterRegistry = new StackdriverMeterRegistry(config, clock);

    @Test
    @Issue("#1325")
    void distributionCountBucketsInfinityBucketIsNotNegative() {
        DistributionSummary ds = DistributionSummary.builder("ds").serviceLevelObjectives(1, 2).register(meterRegistry);
        ds.record(1);
        ds.record(1);
        ds.record(2);
        ds.record(2);
        ds.record(2);
        StackdriverMeterRegistry.Batch batch = meterRegistry.new Batch();
        // count is 0 from previous step, but sum of bucket counts is 5
        HistogramSnapshot histogramSnapshot = ds.takeSnapshot();
        assertThat(histogramSnapshot.count()).isEqualTo(0);
        Distribution distribution = batch.distribution(histogramSnapshot, false);
        List<Long> bucketCountsList = distribution.getBucketCountsList();
        assertThat(bucketCountsList.get(bucketCountsList.size() - 1)).isNotNegative();
    }

    @Test
    @Issue("#2045")
    void batchDistributionWhenHistogramSnapshotIsEmpty() {
        // no SLOs, percentiles, or percentile histogram configured
        // => only infinity bucket histogram
        DistributionSummary ds = DistributionSummary.builder("ds").register(meterRegistry);
        StackdriverMeterRegistry.Batch batch = meterRegistry.new Batch();
        HistogramSnapshot histogramSnapshot = ds.takeSnapshot();
        assertThat(histogramSnapshot.histogramCounts()).containsExactly(new CountAtBucket(Double.POSITIVE_INFINITY, 0));
        assertThat(histogramSnapshot.percentileValues()).isEmpty();
        Distribution distribution = batch.distribution(histogramSnapshot, false);
        assertThat(distribution.getBucketOptions().getExplicitBuckets().getBoundsList()).containsExactly(0d);
        assertThat(distribution.getBucketCountsList()).containsExactly(0L);
    }

    // gh-4868 is an issue when the step count is less than the histogram count
    @Test
    void distributionCountMustEqualBucketCountsSum() {
        DistributionSummary ds = DistributionSummary.builder("ds").serviceLevelObjectives(1, 2).register(meterRegistry);
        ds.record(1);
        ds.record(1);
        ds.record(2);
        ds.record(3);
        StackdriverMeterRegistry.Batch batch = meterRegistry.new Batch();
        HistogramSnapshot histogramSnapshot = ds.takeSnapshot();
        Distribution distribution = batch.distribution(histogramSnapshot, false);
        assertThat(distribution.getCount())
            .isEqualTo(distribution.getBucketCountsList().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    void distributionWithTimerShouldHaveInfinityBucket() {
        StackdriverMeterRegistry.Batch batch = meterRegistry.new Batch();
        Timer timer = Timer.builder("timer")
            .serviceLevelObjectives(Duration.ofMillis(1), Duration.ofMillis(2))
            .register(meterRegistry);
        timer.record(Duration.ofMillis(1));
        timer.record(Duration.ofMillis(2));
        timer.record(Duration.ofMillis(2));
        timer.record(Duration.ofMillis(3));

        HistogramSnapshot histogramSnapshot = timer.takeSnapshot();
        Distribution distribution = batch.distribution(histogramSnapshot, true);
        assertThat(distribution.getCount())
            .isEqualTo(distribution.getBucketCountsList().stream().mapToLong(Long::longValue).sum());
        assertThat(distribution.getBucketOptions().getExplicitBuckets().getBoundsCount()).isEqualTo(2);
        // one more count than boundaries for the infinity bucket
        assertThat(distribution.getBucketCountsList()).hasSize(3);
    }

    @Test
    void distributionWithPercentileHistogram() {
        StackdriverMeterRegistry.Batch batch = meterRegistry.new Batch();
        DistributionSummary ds = DistributionSummary.builder("ds").publishPercentileHistogram().register(meterRegistry);
        ds.record(1);
        ds.record(2);
        ds.record(3);
        ds.record(4);
        ds.record(23);

        Distribution distribution = batch.distribution(ds.takeSnapshot(), false);
        assertThat(distribution.getBucketOptions().getExplicitBuckets().getBoundsList()).hasSize(17)
            .as("trimmed zero count buckets")
            .endsWith(26d);
        assertThat(distribution.getBucketCountsList()).hasSize(18).as("Infinity bucket count should be 0").endsWith(0L);
    }

    @Test
    void distributionWithOnlyClientSidePercentilesHasSingleBound() {
        StackdriverMeterRegistry.Batch batch = meterRegistry.new Batch();
        DistributionSummary ds = DistributionSummary.builder("ds")
            .publishPercentiles(0.5, 0.99)
            .register(meterRegistry);
        ds.record(5);

        Distribution distribution = batch.distribution(ds.takeSnapshot(), false);
        assertThat(distribution.getBucketOptions().getExplicitBuckets().getBoundsList()).containsExactly(0d);
        assertThat(distribution.getBucketCountsList()).containsExactly(1L);
        assertThat(distribution.getCount()).isOne();
    }

    @Test
    void distributionWithClientSidePercentilesAndBuckets() {
        StackdriverMeterRegistry.Batch batch = meterRegistry.new Batch();
        DistributionSummary ds = DistributionSummary.builder("ds")
            .publishPercentiles(0.5, 0.99)
            .serviceLevelObjectives(3, 4, 5)
            .register(meterRegistry);
        ds.record(1);
        ds.record(5);

        Distribution distribution = batch.distribution(ds.takeSnapshot(), false);
        assertThat(distribution.getBucketOptions().getExplicitBuckets().getBoundsList()).containsExactly(3d, 4d, 5d);
        assertThat(distribution.getBucketCountsList()).containsExactly(1L, 0L, 1L, 0L);
    }

    @Test
    void distributionWithOneExplicitBucket() {
        StackdriverMeterRegistry.Batch batch = meterRegistry.new Batch();
        DistributionSummary ds = DistributionSummary.builder("ds").serviceLevelObjectives(3).register(meterRegistry);
        ds.record(1);
        ds.record(5);

        Distribution distribution = batch.distribution(ds.takeSnapshot(), false);
        assertThat(distribution.getBucketOptions().getExplicitBuckets().getBoundsList()).containsExactly(3d);
        assertThat(distribution.getBucketCountsList()).containsExactly(1L, 1L);
    }

    @Test
    @Issue("#5927")
    void meanIsZeroWhenCountIsZero() {
        StackdriverMeterRegistry.Batch batch = meterRegistry.new Batch();
        // halfway through first step
        clock.add(config.step().dividedBy(2));
        // histogram time window will start here
        DistributionSummary ds = DistributionSummary.builder("ds").serviceLevelObjectives(2).register(meterRegistry);
        ds.record(3);
        // 3/4 through the second step; after histogram rollover
        clock.add(config.step().dividedBy(4).multipliedBy(5));
        Distribution distribution = batch.distribution(ds.takeSnapshot(), false);
        assertThat(distribution.getCount()).isZero();
        assertThat(distribution.getMean()).isZero();
    }

    @Test
    @Issue("#6401")
    void distributionWithNoBucketsHasCountInHistogramTimeWindow() {
        StackdriverMeterRegistry.Batch batch = meterRegistry.new Batch();

        // half-way through the first step, example: 30s
        clock.add(config.step().dividedBy(2));
        DistributionSummary ds = DistributionSummary.builder("ds").register(meterRegistry);
        ds.record(3);

        // 1/4 through the second step, example: 1m15s
        clock.add(config.step().dividedBy(4).multipliedBy(3));
        // assert and make count rollover as would happen via scheduled rollover
        assertThat(ds.count()).isOne();
        ds.record(5);

        // 3/4 through the second step, example: 1m45s
        clock.add(config.step().dividedBy(2));
        assertThat(ds.count()).isOne();
        ds.record(7);

        Distribution distribution = batch.distribution(ds.takeSnapshot(), false);
        // still returns count for the previous step
        assertThat(ds.count()).isOne();
        assertThat(distribution.getCount()).isEqualTo(2);
    }

}
