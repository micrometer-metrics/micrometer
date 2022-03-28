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
package io.micrometer.dynatrace.types;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Tests for {@link DynatraceDistributionSummary}.
 *
 * @author Georg Pirklbauer
 * @since 1.9.0
 */
class DynatraceDistributionSummaryTest {
    private static final Offset<Double> OFFSET = Offset.offset(0.0001);
    private static final Meter.Id ID = new Meter.Id("test.id", Tags.empty(), "1", "desc", Meter.Type.DISTRIBUTION_SUMMARY);
    private static final DistributionStatisticConfig DISTRIBUTION_STATISTIC_CONFIG = DistributionStatisticConfig.NONE;
    private static final Clock CLOCK = new MockClock();

    @Test
    void testHasValues() {
        DynatraceDistributionSummary ds = new DynatraceDistributionSummary(ID, CLOCK, DISTRIBUTION_STATISTIC_CONFIG, 1);
        assertThat(ds.hasValues()).isFalse();
        ds.record(3.14);
        assertThat(ds.hasValues()).isTrue();

        // reset, hasValues should be initially false
        ds.takeSummarySnapshotAndReset();
        assertThat(ds.hasValues()).isFalse();

        // add invalid value, hasValues stays false
        ds.record(-1.234);
        assertThat(ds.hasValues()).isFalse();
    }

    @Test
    void testDynatraceDistributionSummary() {
        DynatraceDistributionSummary ds = new DynatraceDistributionSummary(ID, CLOCK, DISTRIBUTION_STATISTIC_CONFIG, 1);
        ds.record(3.14);
        ds.record(4.76);

        assertMinMaxSumCount(ds, 3.14, 4.76, 7.9, 2);
    }

    @Test
    void testDynatraceDistributionSummaryScaled() {
        double scale = 1.5;
        DynatraceDistributionSummary ds = new DynatraceDistributionSummary(ID, CLOCK, DISTRIBUTION_STATISTIC_CONFIG, scale);
        ds.record(3.14);
        ds.record(4.76);

        assertMinMaxSumCount(ds, 3.14 * scale, 4.76 * scale, 7.9 * scale, 2);
    }

    @Test
    void testRecordNegativeIgnored() {
        DynatraceDistributionSummary ds = new DynatraceDistributionSummary(ID, CLOCK, DISTRIBUTION_STATISTIC_CONFIG, 1);
        ds.record(3.14);
        ds.record(-1.234);
        ds.record(4.76);
        ds.record(-6.789);

        assertMinMaxSumCount(ds, 3.14, 4.76, 7.9, 2);
    }

    @Test
    void testMinMaxAreOverwritten() {
        DynatraceDistributionSummary ds = new DynatraceDistributionSummary(ID, CLOCK, DISTRIBUTION_STATISTIC_CONFIG, 1);
        ds.record(3.14);
        ds.record(4.76);
        ds.record(0.123);
        ds.record(8.93);

        assertMinMaxSumCount(ds, 0.123, 8.93, 16.953, 4);
    }


    @Test
    void testGetSnapshotNoReset() {
        DynatraceDistributionSummary ds = new DynatraceDistributionSummary(ID, CLOCK, DISTRIBUTION_STATISTIC_CONFIG, 1);
        ds.record(3.14);
        ds.record(4.76);

        assertMinMaxSumCount(ds.takeSummarySnapshot(), 3.14, 4.76, 7.9, 2);
        // run twice to make sure its not reset in between
        assertMinMaxSumCount(ds.takeSummarySnapshot(), 3.14, 4.76, 7.9, 2);
    }

    @Test
    void testGetSnapshotNoResetWithTimeUnitIgnored() {
        DynatraceDistributionSummary ds = new DynatraceDistributionSummary(ID, CLOCK, DISTRIBUTION_STATISTIC_CONFIG, 1);
        ds.record(3.14);
        ds.record(4.76);

        assertMinMaxSumCount(ds.takeSummarySnapshot(TimeUnit.MINUTES), 3.14, 4.76, 7.9, 2);
        // run twice to make sure its not reset in between
        assertMinMaxSumCount(ds.takeSummarySnapshot(TimeUnit.MINUTES), 3.14, 4.76, 7.9, 2);
    }


    @Test
    void testGetSnapshotAndReset() {
        DynatraceDistributionSummary ds = new DynatraceDistributionSummary(ID, CLOCK, DISTRIBUTION_STATISTIC_CONFIG, 1);
        ds.record(3.14);
        ds.record(4.76);

        assertMinMaxSumCount(ds.takeSummarySnapshotAndReset(), 3.14, 4.76, 7.9, 2);
        // run twice to make sure its not reset in between
        assertMinMaxSumCount(ds.takeSummarySnapshotAndReset(), 0d, 0d, 0d, 0);
    }

    @Test
    void testGetSnapshotAndResetWithTimeUnitIgnored() {
        DynatraceDistributionSummary ds = new DynatraceDistributionSummary(ID, CLOCK, DISTRIBUTION_STATISTIC_CONFIG, 1);
        ds.record(3.14);
        ds.record(4.76);

        assertMinMaxSumCount(ds.takeSummarySnapshotAndReset(TimeUnit.MINUTES), 3.14, 4.76, 7.9, 2);
        // run twice to make sure its not reset in between
        assertMinMaxSumCount(ds.takeSummarySnapshotAndReset(TimeUnit.MINUTES), 0d, 0d, 0d, 0);
    }

    private void assertMinMaxSumCount(DynatraceDistributionSummary ds, double expMin, double expMax, double expTotal, long expCount) {
        assertThat(ds.min()).isCloseTo(expMin, OFFSET);
        assertThat(ds.max()).isCloseTo(expMax, OFFSET);
        assertThat(ds.totalAmount()).isCloseTo(expTotal, OFFSET);
        assertThat(ds.count()).isEqualTo(expCount);
    }

    private void assertMinMaxSumCount(DynatraceSummarySnapshot snapshot, double expMin, double expMax, double expTotal, long expCount) {
        assertThat(snapshot.getMin()).isCloseTo(expMin, OFFSET);
        assertThat(snapshot.getMax()).isCloseTo(expMax, OFFSET);
        assertThat(snapshot.getTotal()).isCloseTo(expTotal, OFFSET);
        assertThat(snapshot.getCount()).isEqualTo(expCount);
    }
}
