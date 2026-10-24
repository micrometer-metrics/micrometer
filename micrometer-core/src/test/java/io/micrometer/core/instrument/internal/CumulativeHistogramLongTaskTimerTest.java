/*
 * Copyright 2026 VMware, Inc.
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
package io.micrometer.core.instrument.internal;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CumulativeHistogramLongTaskTimerTest {

    private final MockClock clock = new MockClock();

    @Test
    @Issue("#2744")
    void countsDoNotGrowWhileTheSameTaskStaysActive() {
        CumulativeHistogramLongTaskTimer timer = slos(nanos(10), nanos(40), nanos(60));
        timer.start();

        for (int i = 0; i < 5; i++) {
            clock.addSeconds(1);
            assertThat(counts(timer)).containsExactly(1, 1, 1);
        }
    }

    @Test
    @Issue("#2744")
    void countsDoNotGrowWhenSnapshotsAreTakenBackToBack() {
        CumulativeHistogramLongTaskTimer timer = slos(nanos(10), nanos(40), nanos(60));
        timer.start();
        clock.addSeconds(1);

        assertThat(counts(timer)).containsExactly(1, 1, 1);
        assertThat(counts(timer)).containsExactly(1, 1, 1);
    }

    @Test
    @Issue("#2744")
    void countsDoNotGrowAsATaskAgesOutOfBuckets() {
        CumulativeHistogramLongTaskTimer timer = slos(nanos(10), nanos(40), nanos(60));
        timer.start();

        clock.addSeconds(1);
        assertThat(counts(timer)).containsExactly(1, 1, 1);

        // older than the 10s bucket, still within the 40s and 60s buckets
        clock.addSeconds(20);
        assertThat(counts(timer)).containsExactly(1, 1, 1);

        // older than every bucket
        clock.addSeconds(60);
        assertThat(counts(timer)).containsExactly(1, 1, 1);
    }

    @Test
    void countsAccumulateOverCompletedTasks() {
        CumulativeHistogramLongTaskTimer timer = slos(nanos(10), nanos(40), nanos(60));
        LongTaskTimer.Sample sample = timer.start();
        clock.addSeconds(1);
        assertThat(counts(timer)).containsExactly(1, 1, 1);

        sample.stop();
        assertThat(counts(timer)).containsExactly(1, 1, 1);

        timer.start();
        clock.addSeconds(1);
        assertThat(counts(timer)).containsExactly(2, 2, 2);
    }

    @Test
    void countsAccumulateOverOverlappingTasks() {
        CumulativeHistogramLongTaskTimer timer = slos(nanos(10), nanos(40), nanos(60));
        timer.start();
        clock.addSeconds(1);
        assertThat(counts(timer)).containsExactly(1, 1, 1);

        timer.start();
        clock.addSeconds(1);
        assertThat(counts(timer)).containsExactly(2, 2, 2);
    }

    @Test
    void aTaskStartingAndFinishingBetweenSnapshotsIsNotCounted() {
        CumulativeHistogramLongTaskTimer timer = slos(nanos(10), nanos(40), nanos(60));
        timer.start();
        clock.addSeconds(1);
        assertThat(counts(timer)).containsExactly(1, 1, 1);

        LongTaskTimer.Sample sample = timer.start();
        clock.addSeconds(1);
        sample.stop();

        assertThat(counts(timer)).containsExactly(1, 1, 1);
    }

    @Test
    void anArrivalCancelledByADepartureInTheSameIntervalIsNotCounted() {
        CumulativeHistogramLongTaskTimer timer = slos(nanos(10));
        timer.start();
        clock.addSeconds(1);
        assertThat(counts(timer)).containsExactly(1);

        // the second task enters the 10s bucket in the same interval in which the first
        // one ages out of it, so the bucket's active count is unchanged and the arrival
        // is lost. This is the dominant inaccuracy of estimating arrivals from a net
        // change, and the class javadoc documents it.
        clock.addSeconds(9);
        timer.start();
        clock.addSeconds(1);

        assertThat(counts(timer)).containsExactly(1);
    }

    @Test
    void countsNeverDecreaseFromOneBucketToTheNext() {
        CumulativeHistogramLongTaskTimer timer = slos(nanos(10), nanos(60));

        // a narrow bucket turns tasks over faster than a wide one, so accumulating the
        // buckets independently lets the narrow one overtake the wide one
        int[] taskStarts = { 15, 25, 55, 65, 95, 105, 135 };
        List<double[]> snapshots = new ArrayList<>();
        for (int second = 1, next = 0; second <= 140; second++) {
            if (next < taskStarts.length && taskStarts[next] == second) {
                timer.start();
                next++;
            }
            clock.addSeconds(1);
            if (second % 20 == 0) {
                snapshots.add(counts(timer));
            }
        }

        // Four of the seven tasks were first seen while younger than 10s. The 60s bucket
        // reaches 5 rather than 7 even though no task ever stopped, because the tasks
        // started at seconds 65 and 105 entered it in intervals where another task aged
        // out of it. Accumulating each bucket on its own would end at [4, 3] and invert
        // the ordering between the buckets; taking the largest increase at or below each
        // bucket is what keeps the wide bucket ahead.
        assertThat(snapshots).containsExactly(new double[] { 1, 1 }, new double[] { 1, 2 }, new double[] { 2, 3 },
                new double[] { 2, 3 }, new double[] { 3, 4 }, new double[] { 3, 4 }, new double[] { 4, 5 });
    }

    @Test
    void countsAreCumulativeWithAPercentileHistogram() {
        CumulativeHistogramLongTaskTimer timer = timer(DistributionStatisticConfig.builder()
            .percentilesHistogram(true)
            .minimumExpectedValue(nanos(1))
            .maximumExpectedValue(nanos(120))
            .build());

        List<double[]> snapshots = new ArrayList<>();
        for (int second = 1; second <= 100; second++) {
            if (second % 10 == 0) {
                timer.start();
            }
            clock.addSeconds(1);
            if (second % 25 == 0) {
                snapshots.add(counts(timer));
            }
        }

        assertThat(snapshots).hasSize(4);
        assertThat(snapshots.get(0)).hasSizeGreaterThan(1);
        assertCountsAreCumulative(snapshots);
        // No task ever leaves the widest bucket during the run, since it is wider than
        // the longest task age, so every start is credited there and none is cancelled by
        // a departure.
        assertThat(lastCount(snapshots.get(3))).isEqualTo(10);
    }

    @Test
    void countsAreCumulativeWithAPositiveInfinityBucket() {
        // the shape OtlpMeterRegistry publishes
        CumulativeHistogramLongTaskTimer timer = slos(nanos(10), Double.POSITIVE_INFINITY);
        LongTaskTimer.Sample first = timer.start();
        clock.addSeconds(1);
        assertThat(counts(timer)).containsExactly(1, 1);

        timer.start();
        clock.addSeconds(1);
        assertThat(counts(timer)).containsExactly(2, 2);

        first.stop();
        assertThat(counts(timer)).containsExactly(2, 2);

        // the remaining task ages out of the 10s bucket but never out of +Inf
        clock.addSeconds(60);
        assertThat(counts(timer)).containsExactly(2, 2);
    }

    @Test
    void onlyTheHistogramCountsAreCumulative() {
        CumulativeHistogramLongTaskTimer timer = timer(DistributionStatisticConfig.builder()
            .serviceLevelObjectives(nanos(10), nanos(40), nanos(60))
            .percentiles(0.5)
            .build());
        LongTaskTimer.Sample sample = timer.start();
        clock.addSeconds(1);
        timer.takeSnapshot();
        clock.addSeconds(1);
        sample.stop();

        HistogramSnapshot snapshot = timer.takeSnapshot();

        assertThat(snapshot.count()).isZero();
        assertThat(snapshot.total()).isZero();
        assertThat(snapshot.max()).isZero();
        assertThat(snapshot.percentileValues()).containsExactly(new ValueAtPercentile(0.5, 0));
        assertThat(snapshot.histogramCounts()).extracting(CountAtBucket::count).containsExactly(1.0, 1.0, 1.0);
    }

    @Test
    void bucketBoundariesArePreserved() {
        CumulativeHistogramLongTaskTimer timer = slos(nanos(10), nanos(40), nanos(60));
        timer.start();
        clock.addSeconds(1);
        timer.takeSnapshot();
        clock.addSeconds(1);

        assertThat(timer.takeSnapshot().histogramCounts()).containsExactly(new CountAtBucket(nanos(10), 1),
                new CountAtBucket(nanos(40), 1), new CountAtBucket(nanos(60), 1));
    }

    @Test
    void noBucketsIsSupported() {
        CumulativeHistogramLongTaskTimer timer = timer(DistributionStatisticConfig.builder().build());
        timer.start();

        clock.addSeconds(1);
        assertThat(counts(timer)).isEmpty();
        clock.addSeconds(1);
        assertThat(counts(timer)).isEmpty();
    }

    private static void assertCountsAreCumulative(List<double[]> snapshots) {
        double[] previous = new double[snapshots.get(0).length];
        for (double[] counts : snapshots) {
            assertThat(counts).isSorted();
            for (int i = 0; i < counts.length; i++) {
                assertThat(counts[i]).isGreaterThanOrEqualTo(previous[i]);
            }
            previous = counts;
        }
    }

    private CumulativeHistogramLongTaskTimer slos(double... slos) {
        return timer(DistributionStatisticConfig.builder().serviceLevelObjectives(slos).build());
    }

    private CumulativeHistogramLongTaskTimer timer(DistributionStatisticConfig config) {
        Meter.Id id = new Meter.Id("my.timer", Tags.empty(), null, null, Meter.Type.LONG_TASK_TIMER);
        return new CumulativeHistogramLongTaskTimer(id, clock, TimeUnit.NANOSECONDS,
                config.merge(DistributionStatisticConfig.DEFAULT));
    }

    private static double[] counts(CumulativeHistogramLongTaskTimer timer) {
        HistogramSnapshot snapshot = timer.takeSnapshot();
        return Arrays.stream(snapshot.histogramCounts()).mapToDouble(CountAtBucket::count).toArray();
    }

    private static double lastCount(double[] counts) {
        return counts[counts.length - 1];
    }

    private static double nanos(int seconds) {
        return Duration.ofSeconds(seconds).toNanos();
    }

}
