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
package io.micrometer.core.instrument.push;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link PushMeterRegistry}.
 */
class PushMeterRegistryTest {

    static ThreadFactory threadFactory = new NamedThreadFactory("PushMeterRegistryTest");

    StepRegistryConfig config = new StepRegistryConfig() {
        @Override
        public Duration step() {
            return Duration.ofMillis(100);
        }

        @Override
        public String prefix() {
            return null;
        }

        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public boolean publishAtStep() {
            return false;
        }
    };

    PushMeterRegistry pushMeterRegistry;

    @AfterEach
    void cleanUp() {
        pushMeterRegistry.close();
    }

    @Test
    void whenUncaughtExceptionInPublish_taskStillScheduled() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        pushMeterRegistry = new ThrowingPushMeterRegistry(config, latch);
        pushMeterRegistry.start(threadFactory);
        assertThat(latch.await(config.step().toMillis() * 3, TimeUnit.MILLISECONDS))
                .as("publish should continue to be scheduled even if an uncaught exception is thrown").isTrue();
    }

    @Test
    void whenUncaughtExceptionInPublish_closeRegistrySuccessful() {
        CountDownLatch latch = new CountDownLatch(1);
        pushMeterRegistry = new ThrowingPushMeterRegistry(config, latch);
        assertThatCode(() -> pushMeterRegistry.close()).doesNotThrowAnyException();
    }

    // This test asserts timing based on system clock. Code execution will be
    // significantly slower when debugging, causing assertions to fail.
    // Repeated to get different times relative to the epoch steps
    @Issue("#2818")
    @RepeatedTest(6)
    void publishTiming(RepetitionInfo info) throws InterruptedException {
        TimeRecordingPushMeterRegistry timeRecordingPushMeterRegistry = new TimeRecordingPushMeterRegistry(config,
                Clock.SYSTEM);
        pushMeterRegistry = timeRecordingPushMeterRegistry;
        if (info.getCurrentRepetition() == 1) {
            // first iteration has too much delay in execution to reliably assert
            return;
        }

        assertPublishTiming(timeRecordingPushMeterRegistry, timeRecordingPushMeterRegistry.firstPublishLatch, 0);

        // stop and start with a little delay
        timeRecordingPushMeterRegistry.stop();
        long sleepTime = config.step().toMillis() / 5;
        Thread.sleep(sleepTime);
        timeRecordingPushMeterRegistry.start(threadFactory);

        assertPublishTiming(timeRecordingPushMeterRegistry, timeRecordingPushMeterRegistry.secondPublishLatch,
                sleepTime + 5 // takes some time to run other code
        );
    }

    private void assertPublishTiming(TimeRecordingPushMeterRegistry timeRecordingPushMeterRegistry,
            CountDownLatch publishLatch, long sleep) throws InterruptedException {
        long startTimeMillis = timeRecordingPushMeterRegistry.lastStartTimeMillis;

        long expectedPublishTime = startTimeMillis + config.step().toMillis() + 1 - sleep;
        assertThat(publishLatch.await(config.step().toMillis() * 2, TimeUnit.MILLISECONDS)).isTrue();
        // scheduler timing and execution time of code make asserting expected elapsed
        // time imprecise
        assertThat(timeRecordingPushMeterRegistry.lastPublishTimeMillis).isCloseTo(expectedPublishTime,
                Offset.offset(7L));
    }

    static class ThrowingPushMeterRegistry extends StepMeterRegistry {

        final CountDownLatch countDownLatch;

        public ThrowingPushMeterRegistry(StepRegistryConfig config, CountDownLatch countDownLatch) {
            super(config, new MockClock());
            this.countDownLatch = countDownLatch;
        }

        @Override
        protected void publish() {
            countDownLatch.countDown();
            throw new RuntimeException("in ur base");
        }

        @Override
        protected TimeUnit getBaseTimeUnit() {
            return TimeUnit.MICROSECONDS;
        }

    }

    static class TimeRecordingPushMeterRegistry extends StepMeterRegistry {

        long lastPublishTimeMillis;

        long lastStartTimeMillis;

        CountDownLatch firstPublishLatch = new CountDownLatch(1);

        CountDownLatch secondPublishLatch = new CountDownLatch(1);

        public TimeRecordingPushMeterRegistry(StepRegistryConfig config, Clock clock) {
            super(config, clock);
            start(threadFactory);
        }

        @Override
        protected TimeUnit getBaseTimeUnit() {
            return TimeUnit.MILLISECONDS;
        }

        @Override
        protected void publish() {
            lastPublishTimeMillis = clock.wallTime();
            if (firstPublishLatch.getCount() != 0) {
                firstPublishLatch.countDown();
            }
            else {
                secondPublishLatch.countDown();
            }
        }

        @Override
        public void start(ThreadFactory threadFactory) {
            super.start(threadFactory);
            lastStartTimeMillis = clock.wallTime();
        }

        @Override
        protected long getRegistryStartMillis() {
            return super.getRegistryStartMillis();
        }

    }

}
