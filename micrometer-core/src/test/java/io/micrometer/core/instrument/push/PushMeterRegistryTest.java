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

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.junit.jupiter.api.AfterEach;
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
            return Duration.ofMillis(10);
        }

        @Override
        public String prefix() {
            return null;
        }

        @Override
        public String get(String key) {
            return null;
        }
    };
    CountDownLatch latch = new CountDownLatch(2);
    PushMeterRegistry pushMeterRegistry = new ThrowingPushMeterRegistry(config, latch);

    @AfterEach
    void cleanUp() {
        pushMeterRegistry.close();
    }

    @Test
    void whenUncaughtExceptionInPublish_taskStillScheduled() throws InterruptedException {
        pushMeterRegistry.start(threadFactory);
        assertThat(latch.await(500, TimeUnit.MILLISECONDS))
                .as("publish should continue to be scheduled even if an uncaught exception is thrown")
                .isTrue();
    }

    @Test
    void whenUncaughtExceptionInPublish_closeRegistrySuccessful() {
        assertThatCode(() -> pushMeterRegistry.close()).doesNotThrowAnyException();
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
}
