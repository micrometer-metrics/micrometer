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
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.lang.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StackdriverMeterRegistry}
 */
class StackdriverMeterRegistryTest {

    StackdriverMeterRegistry meterRegistry = new StackdriverMeterRegistry(new StackdriverConfig() {
        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public String projectId() {
            return "doesnotmatter";
        }

        @Override
        @Nullable
        public String get(String key) {
            return null;
        }
    }, new MockClock());

    @Test
    @Issue("#1325")
    void distributionCountBucketsInfinityBucketIsNotNegative() {
        StackdriverMeterRegistry.Batch batch = meterRegistry.new Batch();
        // count is 4, but sum of bucket counts is 5 due to inconsistent snapshotting
        HistogramSnapshot histogramSnapshot = new HistogramSnapshot(4, 14.7, 5, null, new CountAtBucket[]{new CountAtBucket(1.0, 2), new CountAtBucket(2.0, 5)}, null);
        Distribution distribution = batch.distribution(histogramSnapshot, false);
        List<Long> bucketCountsList = distribution.getBucketCountsList();
        assertThat(bucketCountsList.get(bucketCountsList.size() - 1)).isNotNegative();
    }

    @Test
    @Issue("#2045")
    void batchDistributionWhenHistogramSnapshotIsEmpty() {
        StackdriverMeterRegistry.Batch batch = meterRegistry.new Batch();
        HistogramSnapshot histogramSnapshot = HistogramSnapshot.empty(0, 0.0, 0.0);
        Distribution distribution = batch.distribution(histogramSnapshot, false);
        assertThat(distribution.getBucketOptions().getExplicitBuckets().getBoundsCount()).isEqualTo(1);
        assertThat(distribution.getBucketCountsList()).hasSize(1);
    }
}
