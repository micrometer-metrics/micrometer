/*
 * Copyright 2024 VMware, Inc.
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

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DynatraceLongTaskTimer}.
 */
class DynatraceLongTaskTimerTest {

    private static final Meter.Id ID = new Meter.Id("test.id", Tags.empty(), "1", "desc",
            Meter.Type.DISTRIBUTION_SUMMARY);

    private static final DistributionStatisticConfig DISTRIBUTION_STATISTIC_CONFIG = DistributionStatisticConfig.NONE;

    private static final MockClock CLOCK = new MockClock();

    @Test
    void singleTaskValuesAreRecorded() throws InterruptedException {
        DynatraceLongTaskTimer ltt = new DynatraceLongTaskTimer(ID, CLOCK, TimeUnit.MILLISECONDS,
                DISTRIBUTION_STATISTIC_CONFIG, false);
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        CountDownLatch taskHasBeenRunningLatch = new CountDownLatch(1);
        CountDownLatch stopLatch = new CountDownLatch(1);

        executorService.submit(() -> ltt.record(() -> {
            CLOCK.add(Duration.ofMillis(100));
            taskHasBeenRunningLatch.countDown();

            try {
                // wait until the snapshot has been taken
                assertThat(stopLatch.await(300, TimeUnit.MILLISECONDS)).isTrue();
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }));

        assertThat(taskHasBeenRunningLatch.await(300, TimeUnit.MILLISECONDS)).isTrue();

        DynatraceSummarySnapshot snapshot = ltt.takeSummarySnapshot();
        // can release the background task
        stopLatch.countDown();

        assertThat(snapshot.getMin()).isEqualTo(100);
        assertThat(snapshot.getMax()).isEqualTo(100);
        assertThat(snapshot.getCount()).isEqualTo(1);
        // in the case of count == 1, the total has to be equal to min and max
        assertThat(snapshot.getTotal()).isEqualTo(snapshot.getMin()).isEqualTo(snapshot.getMax());
    }

    @Test
    void parallelTasksValuesAreRecorded() throws InterruptedException {
        DynatraceLongTaskTimer ltt = new DynatraceLongTaskTimer(ID, CLOCK, TimeUnit.MILLISECONDS,
                DISTRIBUTION_STATISTIC_CONFIG, false);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        CountDownLatch firstTaskHasBeenRunning = new CountDownLatch(1);
        // both tasks need to be running for a while before we take the snapshot
        CountDownLatch bothTasksHaveBeenRunningLatch = new CountDownLatch(2);
        CountDownLatch stopLatch = new CountDownLatch(1);

        // Task 1
        executorService.submit(() -> ltt.record(() -> {
            try {
                CLOCK.add(Duration.ofMillis(40));

                // task 1 starts first, runs for 40ms (see CLOCK.add(Duration) above),
                // then the second task starts. The second task can start after this
                // latch has counted down (unblocked) the other thread.
                firstTaskHasBeenRunning.countDown();
                bothTasksHaveBeenRunningLatch.countDown();

                // wait until the snapshot has been taken
                assertThat(stopLatch.await(300, TimeUnit.MILLISECONDS)).isTrue();
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }));

        // Task 1 (see above) has been running for 40ms, and is still running now (until
        // stopLatch is unblocked).
        assertThat(firstTaskHasBeenRunning.await(300, TimeUnit.MILLISECONDS)).isTrue();

        // Task 2
        executorService.submit(() -> ltt.record(() -> {
            try {
                // At this point both tasks are running.
                // Adding to the clock here means that both tasks are running at the
                // same time and adding 30ms adds 30ms to both running task.
                CLOCK.add(Duration.ofMillis(30));

                // Release the latch: this means that both tasks
                // have been running and the time has been added to the clock. Both
                // tasks will (conceptually) continue to run until the stopLatch is
                // unblocked.
                bothTasksHaveBeenRunningLatch.countDown();

                // wait until the snapshot has been taken
                assertThat(stopLatch.await(300, TimeUnit.MILLISECONDS)).isTrue();
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }));

        // both tasks have been running for different lengths of time (task 1 for a total
        // of 70ms (40+30ms), and task 2 for a total of 30ms).
        assertThat(bothTasksHaveBeenRunningLatch.await(300, TimeUnit.MILLISECONDS)).isTrue();

        // take a snapshot of the state where both tasks are running for different lengths
        // of time.
        DynatraceSummarySnapshot snapshot = ltt.takeSummarySnapshot();

        // the two running tasks are now allowed to exit.
        stopLatch.countDown();

        // Task 1 has been "running" for 70ms at the time of recording and will
        // supply the max
        assertThat(snapshot.getMax()).isEqualTo(70);
        // Task 2 has been "running" for only 30ms at the time of recording and
        // will supply the min
        assertThat(snapshot.getMin()).isEqualTo(30);
        // Both tasks have been running in parallel.
        // After the second CLOCK.add(Duration) is called, the first task has been running
        // for 70ms, and the second task has been running for 30ms
        // together, they have been running for 100ms in total.
        assertThat(snapshot.getTotal()).isEqualTo(100);
        // Two tasks were running in parallel.
        assertThat(snapshot.getCount()).isEqualTo(2);
        // On the clock, 70ms have passed. MockClock starts at 1, that's why the result
        // here is 71 instead of 70.
        assertThat(CLOCK.wallTime()).isEqualTo(71);
    }

}
