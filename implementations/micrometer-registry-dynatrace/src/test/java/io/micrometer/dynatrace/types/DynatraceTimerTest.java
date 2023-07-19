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
import io.micrometer.core.instrument.distribution.pause.NoPauseDetector;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Tests for {@link DynatraceTimer}.
 *
 * @author Georg Pirklbauer
 */
class DynatraceTimerTest {

    private static final Offset<Double> OFFSET = Offset.offset(0.0001);

    private static final Clock CLOCK = new MockClock();

    private static final TimeUnit BASE_TIME_UNIT = TimeUnit.MILLISECONDS;

    private static final Meter.Id ID = new Meter.Id("test.id", Tags.empty(), "1", "desc", Meter.Type.TIMER);

    private static final DistributionStatisticConfig DISTRIBUTION_STATISTIC_CONFIG = DistributionStatisticConfig.NONE;

    private static final PauseDetector PAUSE_DETECTOR = new NoPauseDetector();

    @Test
    void testTimerCount() {
        DynatraceTimer timer = new DynatraceTimer(ID, CLOCK, DISTRIBUTION_STATISTIC_CONFIG, PAUSE_DETECTOR,
                BASE_TIME_UNIT);

        assertThat(timer.count()).isZero();
        timer.record(Duration.ofMillis(314));
        assertThat(timer.count()).isEqualTo(1);
        timer.record(Duration.ofMillis(476));
        assertThat(timer.count()).isEqualTo(2);

        timer.takeSummarySnapshotAndReset(BASE_TIME_UNIT);
        assertThat(timer.count()).isZero();
    }

    @Test
    void testTimerValuesAreRecorded() {
        DynatraceTimer timer = new DynatraceTimer(ID, CLOCK, DISTRIBUTION_STATISTIC_CONFIG, PAUSE_DETECTOR,
                BASE_TIME_UNIT);

        timer.record(Duration.ofMillis(314));
        timer.record(Duration.ofMillis(476));

        assertMinMaxSumCount(timer, 314, 476, 790, 2);
    }

    @Test
    void testNegativeValuesIgnored() {
        DynatraceTimer timer = new DynatraceTimer(ID, CLOCK, DISTRIBUTION_STATISTIC_CONFIG, PAUSE_DETECTOR,
                BASE_TIME_UNIT);
        timer.record(-100, TimeUnit.MILLISECONDS);
        timer.record(Duration.ofMillis(-100));
        assertMinMaxSumCount(timer, 0, 0, 0, 0);
    }

    @Test
    void testMinMaxAreOverwritten() {
        DynatraceTimer timer = new DynatraceTimer(ID, CLOCK, DISTRIBUTION_STATISTIC_CONFIG, PAUSE_DETECTOR,
                BASE_TIME_UNIT);
        timer.record(Duration.ofMillis(314));
        timer.record(Duration.ofMillis(476));
        assertMinMaxSumCount(timer, 314, 476, 790, 2);
        timer.record(Duration.ofMillis(123));
        timer.record(Duration.ofMillis(579));
        assertMinMaxSumCount(timer, 123, 579, 1492, 4);
    }

    @Test
    void testGetSnapshotAndReset() {
        DynatraceTimer timer = new DynatraceTimer(ID, CLOCK, DISTRIBUTION_STATISTIC_CONFIG, PAUSE_DETECTOR,
                BASE_TIME_UNIT);
        timer.record(Duration.ofMillis(314));
        timer.record(Duration.ofMillis(476));

        assertMinMaxSumCount(timer.takeSummarySnapshotAndReset(BASE_TIME_UNIT), 314, 476, 790, 2);
        // check that the timer was indeed reset
        assertMinMaxSumCount(timer, 0d, 0d, 0d, 0);
    }

    @Test
    void testGetSnapshot() {
        DynatraceTimer timer = new DynatraceTimer(ID, CLOCK, DISTRIBUTION_STATISTIC_CONFIG, PAUSE_DETECTOR,
                BASE_TIME_UNIT);
        timer.record(Duration.ofMillis(314));
        timer.record(Duration.ofMillis(476));

        assertMinMaxSumCount(timer.takeSummarySnapshot(BASE_TIME_UNIT), 314, 476, 790, 2);
        // check that the timer was not reset
        assertMinMaxSumCount(timer, 314, 476, 790, 2);
    }

    @Test
    void testDifferentTimeUnits() {
        // set up the timer to record in Nanoseconds
        DynatraceTimer timer = new DynatraceTimer(ID, CLOCK, DISTRIBUTION_STATISTIC_CONFIG, PAUSE_DETECTOR,
                TimeUnit.NANOSECONDS);
        Duration oneSecond = Duration.ofSeconds(1);
        timer.record(oneSecond);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isCloseTo(1_000_000_000, OFFSET);
        // when requesting the time in a different unit, it is converted to that unit
        // before being returned
        assertThat(timer.totalTime(TimeUnit.SECONDS)).isCloseTo(1d, OFFSET);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isCloseTo(1000, OFFSET);
    }

    @Test
    void testConvertIfNecessary() {
        TimeUnit unit = TimeUnit.MILLISECONDS;
        DynatraceTimer timer = new DynatraceTimer(ID, CLOCK, DISTRIBUTION_STATISTIC_CONFIG, PAUSE_DETECTOR, unit);

        DynatraceSummarySnapshot snapshot = new DynatraceSummarySnapshot(1000.0, 2000.0, 3000.0, 2);
        DynatraceSummarySnapshot unconverted = timer.convertIfNecessary(snapshot, unit);
        assertThat(unconverted).isEqualTo(snapshot);

        DynatraceSummarySnapshot converted = timer.convertIfNecessary(snapshot, TimeUnit.SECONDS);
        assertMinMaxSumCount(converted, 1, 2, 3, 2);
    }

    @Test
    void testSnapshotWithoutTimeUnitAndReset() {
        DynatraceTimer timer = new DynatraceTimer(ID, CLOCK, DISTRIBUTION_STATISTIC_CONFIG, PAUSE_DETECTOR,
                TimeUnit.MILLISECONDS);
        timer.record(Duration.ofSeconds(1));

        assertMinMaxSumCount(timer.takeSummarySnapshotAndReset(), 1000, 1000, 1000, 1);
        assertMinMaxSumCount(timer.takeSummarySnapshot(), 0, 0, 0, 0);
    }

    @Test
    void testSnapshotWithoutTimeUnit_shouldReturnInBaseUnit() {
        DynatraceTimer timerMillis = new DynatraceTimer(ID, CLOCK, DISTRIBUTION_STATISTIC_CONFIG, PAUSE_DETECTOR,
                TimeUnit.MILLISECONDS);
        DynatraceTimer timerDays = new DynatraceTimer(ID, CLOCK, DISTRIBUTION_STATISTIC_CONFIG, PAUSE_DETECTOR,
                TimeUnit.DAYS);

        timerMillis.record(Duration.ofMillis(314));
        // add 3000 millis as seconds
        timerMillis.record(Duration.ofSeconds(3));

        timerDays.record(Duration.ofDays(10));
        // add 1 day as hours
        timerDays.record(Duration.ofHours(24));

        assertMinMaxSumCount(timerMillis.takeSummarySnapshot(), 314, 3000, 3314, 2);
        assertMinMaxSumCount(timerDays.takeSummarySnapshot(), 1, 10, 11, 2);
    }

    @Test
    void testUseAllRecordInterfaces() {
        MockClock clock = new MockClock();
        DynatraceTimer timer = new DynatraceTimer(ID, clock, DISTRIBUTION_STATISTIC_CONFIG, PAUSE_DETECTOR,
                BASE_TIME_UNIT);

        // Runnable
        timer.record(() -> {
            // Simulate the passing of time by using the MockClock interface
            clock.add(Duration.ofMillis(100));
        });

        // Supplier
        timer.record(() -> {
            clock.add(Duration.ofMillis(200));
            return 0;
        });

        // Duration
        timer.record(Duration.ofMillis(300));

        // Amount & Unit
        timer.record(400, TimeUnit.MILLISECONDS);

        assertMinMaxSumCount(timer.takeSummarySnapshot(BASE_TIME_UNIT), 100, 400, 1000, 4);
    }

    private void assertMinMaxSumCount(DynatraceTimer timer, double expMin, double expMax, double expTotal,
            long expCount) {
        @SuppressWarnings("deprecation")
        double min = timer.min(BASE_TIME_UNIT);
        assertThat(min).isCloseTo(expMin, OFFSET);
        assertThat(timer.max(BASE_TIME_UNIT)).isCloseTo(expMax, OFFSET);
        assertThat(timer.totalTime(BASE_TIME_UNIT)).isCloseTo(expTotal, OFFSET);
        assertThat(timer.count()).isEqualTo(expCount);
    }

    private void assertMinMaxSumCount(DynatraceSummarySnapshot snapshot, double expMin, double expMax, double expTotal,
            long expCount) {
        assertThat(snapshot.getMin()).isCloseTo(expMin, OFFSET);
        assertThat(snapshot.getMax()).isCloseTo(expMax, OFFSET);
        assertThat(snapshot.getTotal()).isCloseTo(expTotal, OFFSET);
        assertThat(snapshot.getCount()).isEqualTo(expCount);
    }

}
