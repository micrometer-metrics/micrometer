/*
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.core.instrument.step;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link StepFunctionTimer}.
 *
 * @author Johnny Lim
 */
class StepFunctionTimerTest {

    MockClock clock = new MockClock();

    @Test
    void totalTimeWhenStateObjectChangedToNullShouldWorkWithChangedTimeUnit() {
        StepFunctionTimer<Object> functionTimer = new StepFunctionTimer<>(mock(Meter.Id.class), clock, 1000L,
                new Object(), (o) -> 1L, (o) -> 1d, TimeUnit.SECONDS, TimeUnit.SECONDS);
        clock.add(Duration.ofSeconds(1));
        assertThat(functionTimer.totalTime(TimeUnit.SECONDS)).isEqualTo(1d);
        assertThat(functionTimer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(1000d);
        System.gc();
        assertThat(functionTimer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(1000d);
        assertThat(functionTimer.totalTime(TimeUnit.SECONDS)).isEqualTo(1d);
    }

    @SuppressWarnings("ConstantConditions")
    @Issue("#1814")
    @Test
    void meanShouldWorkIfTotalNotCalled() {
        Queue<Long> counts = new ArrayDeque<>();
        counts.add(2L);
        counts.add(5L);
        counts.add(10L);

        Queue<Double> totalTimes = new ArrayDeque<>();
        totalTimes.add(150.0);
        totalTimes.add(300.0);
        totalTimes.add(1000.0);

        Duration stepDuration = Duration.ofMillis(10);
        StepFunctionTimer<Object> ft = new StepFunctionTimer<>(mock(Meter.Id.class), clock, stepDuration.toMillis(),
                new Object(), (o) -> counts.poll(), (o) -> totalTimes.poll(), TimeUnit.SECONDS, TimeUnit.SECONDS);

        assertThat(ft.count()).isEqualTo(0.0);

        clock.add(stepDuration);
        assertThat(ft.mean(TimeUnit.SECONDS)).isEqualTo(300.0 / 5L);

        clock.add(stepDuration);
        assertThat(ft.mean(TimeUnit.SECONDS)).isEqualTo(700.0 / 5L);
    }

    @Test
    void closingRolloverPartialStep() {
        Queue<Long> counts = new ArrayDeque<>();
        counts.add(2L);
        counts.add(5L);
        counts.add(10L);

        Queue<Double> totalTimes = new ArrayDeque<>();
        totalTimes.add(150.0);
        totalTimes.add(300.0);
        totalTimes.add(1000.0);

        Duration stepDuration = Duration.ofMillis(10);
        StepFunctionTimer<Object> timer = new StepFunctionTimer<>(mock(Meter.Id.class), clock, stepDuration.toMillis(),
                new Object(), (o) -> counts.poll(), (o) -> totalTimes.poll(), TimeUnit.SECONDS, TimeUnit.SECONDS);

        assertThat(timer.count()).isZero();
        assertThat(timer.totalTime(TimeUnit.SECONDS)).isZero();

        timer._closingRollover();

        assertThat(timer.count()).isEqualTo(2);
        assertThat(timer.totalTime(TimeUnit.SECONDS)).isEqualTo(150);

        clock.add(stepDuration);

        assertThat(timer.count()).isEqualTo(2);
        assertThat(timer.totalTime(TimeUnit.SECONDS)).isEqualTo(150);
    }

    @Issue("#2489")
    @Test
    void countAndTotalTimeShouldNotGoNegativeWhenFunctionsReset() {
        AtomicLong count = new AtomicLong(100);
        AtomicLong totalTimeNanos = new AtomicLong(TimeUnit.SECONDS.toNanos(50));
        Duration stepDuration = Duration.ofSeconds(60);
        StepFunctionTimer<Object> timer = new StepFunctionTimer<>(mock(Meter.Id.class), clock, stepDuration.toMillis(),
                new Object(), (o) -> count.get(), (o) -> totalTimeNanos.get(), TimeUnit.NANOSECONDS, TimeUnit.SECONDS);

        clock.add(stepDuration);
        assertThat(timer.count()).isEqualTo(100);
        assertThat(timer.totalTime(TimeUnit.SECONDS)).isEqualTo(50);

        // The count and totalTime functions are expected to be monotonically
        // increasing, but they can decrease on a reset. This must not record a
        // negative count/total for the interval.
        count.set(0);
        totalTimeNanos.set(0);
        clock.add(stepDuration);
        assertThat(timer.count()).isEqualTo(0);
        assertThat(timer.totalTime(TimeUnit.SECONDS)).isEqualTo(0);
    }

}
