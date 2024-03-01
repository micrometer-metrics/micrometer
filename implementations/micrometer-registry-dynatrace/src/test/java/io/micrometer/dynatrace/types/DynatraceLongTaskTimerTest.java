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

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.assertj.core.data.Offset;
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

    private static final Offset<Double> TOLERANCE = Offset.offset(0.000001);

    @Test
    void singleTaskValuesAreRecorded() throws InterruptedException {
        DynatraceLongTaskTimer ltt = new DynatraceLongTaskTimer(ID, CLOCK, TimeUnit.MILLISECONDS,
                DISTRIBUTION_STATISTIC_CONFIG, false);
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        CountDownLatch taskHasBeenRunningLatch = new CountDownLatch(1);
        CountDownLatch stopLatch = new CountDownLatch(1);

        executorService.submit(() -> {
            ltt.record(() -> {
                CLOCK.add(Duration.ofMillis(100));
                taskHasBeenRunningLatch.countDown();

                try {
                    // wait until the snapshot has been taken
                    assertThat(stopLatch.await(300, TimeUnit.MILLISECONDS)).isTrue();
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        });

        assertThat(taskHasBeenRunningLatch.await(300, TimeUnit.MILLISECONDS)).isTrue();

        DynatraceSummarySnapshot snapshot = ltt.takeSummarySnapshot();
        // can release the background task
        stopLatch.countDown();

        assertThat(snapshot.getMin()).isCloseTo(100, TOLERANCE);
        assertThat(snapshot.getMax()).isCloseTo(100, TOLERANCE);
        assertThat(snapshot.getCount()).isEqualTo(1);
        // in the case of count == 1, the total has to be equal to min and max
        assertThat(snapshot.getTotal()).isGreaterThan(0)
            .isCloseTo(snapshot.getMin(), TOLERANCE)
            .isCloseTo(snapshot.getMax(), TOLERANCE);
    }

    /**
     * This test *could* be done with the MockClock, but it gets pretty unintuitive. That
     * is because when adding to the MockClock in one Thread, it automatically adds to the
     * other thread as well and time is basically "double-counted".
     */
    @Test
    void parallelTasksValuesAreRecorded() throws InterruptedException {
        // use the system clock, as it is much easier to understand than when using the
        // MockClock (When using MockClock, adding to the clock in two separate threads
        // basically means that two things happen at the same time).
        DynatraceLongTaskTimer ltt = new DynatraceLongTaskTimer(ID, Clock.SYSTEM, TimeUnit.MILLISECONDS,
                DISTRIBUTION_STATISTIC_CONFIG, false);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        // both tasks need to be running for a while before we take the snapshot
        CountDownLatch taskHasBeenRunningLatch = new CountDownLatch(2);
        CountDownLatch stopLatch = new CountDownLatch(1);

        executorService.submit(() -> {
            ltt.record(() -> {
                try {
                    Thread.sleep(70);
                    taskHasBeenRunningLatch.countDown();

                    // wait until the snapshot has been taken
                    assertThat(stopLatch.await(300, TimeUnit.MILLISECONDS)).isTrue();
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        });

        executorService.submit(() -> {
            ltt.record(() -> {
                try {
                    Thread.sleep(30);
                    taskHasBeenRunningLatch.countDown();

                    // wait until the snapshot has been taken
                    assertThat(stopLatch.await(300, TimeUnit.MILLISECONDS)).isTrue();
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        });

        assertThat(taskHasBeenRunningLatch.await(300, TimeUnit.MILLISECONDS)).isTrue();

        DynatraceSummarySnapshot snapshot = ltt.takeSummarySnapshot();
        // can release the background tasks
        stopLatch.countDown();

        // the first Thread has been "running" for ~30ms at the time of recording and will
        // supply the min
        assertThat(snapshot.getMin()).isGreaterThanOrEqualTo(30).isLessThan(100);
        // the second Thread has been "running" for ~70ms at the time of recording and
        // will supply the max
        assertThat(snapshot.getMax()).isGreaterThanOrEqualTo(70).isLessThan(100);
        // the min is greater than 30, and the max is greater than 70, so together they
        // have to be greater than 100.
        assertThat(snapshot.getTotal()).isGreaterThan(100);
        assertThat(snapshot.getCount()).isEqualTo(2);
    }

}
