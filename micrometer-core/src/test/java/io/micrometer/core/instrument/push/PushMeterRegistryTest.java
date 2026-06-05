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
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static java.util.concurrent.TimeUnit.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * Tests for {@link PushMeterRegistry}.
 */
class PushMeterRegistryTest {

    static final Duration STEP_DURATION = Duration.ofMillis(10);

    StepRegistryConfig config = new StepRegistryConfig() {
        @Override
        public Duration step() {
            return STEP_DURATION;
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

    @Test
    void whenUncaughtExceptionInPublish_taskStillScheduled() throws InterruptedException {
        ThreadFactory threadFactory = new NamedThreadFactory("PushMeterRegistryTest");
        PushMeterRegistry pushMeterRegistry = new ThrowingPushMeterRegistry(config, latch);
        pushMeterRegistry.start(threadFactory);
        assertThat(latch.await(5, TimeUnit.SECONDS))
            .as("publish should continue to be scheduled even if an uncaught exception is thrown")
            .isTrue();
        pushMeterRegistry.close();
    }

    @Test
    void whenUncaughtExceptionInPublish_closeRegistrySuccessful() {
        PushMeterRegistry pushMeterRegistry = new ThrowingPushMeterRegistry(config, latch);
        assertThatCode(() -> pushMeterRegistry.close()).doesNotThrowAnyException();
    }

    @Test
    @Issue("#3712")
    void publishOnlyHappensOnceWithMultipleClose() {
        CountingPushMeterRegistry pushMeterRegistry = new CountingPushMeterRegistry(config, Clock.SYSTEM);
        pushMeterRegistry.close();
        assertThat(pushMeterRegistry.publishCount.get()).isOne();
        pushMeterRegistry.close();
        assertThat(pushMeterRegistry.publishCount.get()).isOne();
    }

    @Test
    @Issue("#3711")
    void doNotPublishAgainOnClose_whenScheduledPublishInProgress() throws InterruptedException {
        CountDownLatch publishStarted = new CountDownLatch(1);
        CountDownLatch publishCanFinish = new CountDownLatch(1);

        CountingPushMeterRegistry registry = new CountingPushMeterRegistry(config, Clock.SYSTEM) {
            @Override
            protected void publish() {
                publishStarted.countDown();
                try {
                    if (!publishCanFinish.await(5, SECONDS)) {
                        throw new RuntimeException("Timeout waiting for publish to finish");
                    }
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                super.publish();
            }
        };

        Thread scheduledPublishThread = new Thread(registry::publishSafelyOrSkipIfInProgress, "scheduledPublishThread");
        scheduledPublishThread.start();

        assertThat(publishStarted.await(5, SECONDS)).as("scheduled publish should start").isTrue();

        Thread closeThread = new Thread(registry::close, "closeThread");
        closeThread.start();

        // This guarantees close() has executed and skipped publishing before we let the
        // scheduled publish finish.
        await().atMost(Duration.ofSeconds(5))
            .pollInterval(10, MILLISECONDS)
            .untilAsserted(() -> assertThat(closeThread.getState())
                .as("closeThread should block and transition to WAITING state while publish is in progress")
                .isEqualTo(Thread.State.WAITING));

        // Let the scheduled publish finish
        publishCanFinish.countDown();

        scheduledPublishThread.join(5000);
        closeThread.join(5000);

        assertThat(registry.publishCount.get())
            .as("publish should only be called once (by the scheduled publish) and not again by close()")
            .isOne();
    }

    @Test
    @Issue("#2818")
    void publishTimeIsRandomizedWithinStep() {
        Duration startTime = Duration.ofMillis(4);
        MockClock clock = new MockClock();
        clock.add(-1, MILLISECONDS); // set time to 0
        clock.add(startTime);
        PushMeterRegistry registry = new CountingPushMeterRegistry(config, clock);
        long minOffsetMillis = 8; // 4 (start) + 8 (offset) = 12 (2ms into next step)
        // exclusive upper bound
        long maxOffsetMillis = 14; // 4 (start) + 14 (offset) = 18 (8ms into next step;
                                   // 80% of step is 8ms)
        Set<Long> observedDelays = new HashSet<>((int) (maxOffsetMillis - minOffsetMillis));
        IntStream.range(0, 10_000).forEach(i -> {
            long delay = registry.calculateInitialDelay();
            // isBetween is inclusive; subtract 1 from exclusive max offset
            assertThat(delay)
                .as("calculated initial delay should be between %d and %d ms (inclusive)", minOffsetMillis, maxOffsetMillis - 1)
                .isBetween(minOffsetMillis, maxOffsetMillis - 1);
            observedDelays.add(delay);
        });
        List<Long> expectedDelays = LongStream.range(minOffsetMillis, maxOffsetMillis)
            .boxed()
            .collect(Collectors.toList());
        assertThat(observedDelays)
            .as("all possible delays within the step should be observed across 10,000 iterations")
            .containsExactlyElementsOf(expectedDelays);
    }

    @Test
    @Issue("#3872")
    void waitForScheduledPublishToFinish_whenClosedWhilePublishIsInProgress() throws InterruptedException {
        CountDownLatch publishStarted = new CountDownLatch(1);
        CountDownLatch publishCanFinish = new CountDownLatch(1);

        CountingPushMeterRegistry registry = new CountingPushMeterRegistry(config, Clock.SYSTEM) {
            @Override
            protected void publish() {
                publishStarted.countDown();
                try {
                    if (!publishCanFinish.await(5, SECONDS)) {
                        throw new RuntimeException("Timeout waiting for publish to finish");
                    }
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        // start scheduled publish but don't let it finish yet
        Thread scheduledPublishingThread = new Thread(() -> registry.publishSafelyOrSkipIfInProgress(),
                "testScheduledMetricsPublisherThread");
        scheduledPublishingThread.start();

        // wait for publish to start
        assertThat(publishStarted.await(5, SECONDS)).as("scheduled publish should start and block inside publish()")
            .isTrue();

        // close registry during publish
        CountDownLatch closeFinished = new CountDownLatch(1);
        Thread closeThread = new Thread(() -> {
            registry.close();
            closeFinished.countDown();
        }, "simulatedShutdownHookThread");
        closeThread.start();

        // Verify that close is blocked (waiting for publish to finish)
        await().atMost(Duration.ofSeconds(5))
            .pollInterval(10, MILLISECONDS)
            .untilAsserted(() -> assertThat(closeThread.getState())
                .as("closeThread should block and transition to WAITING state while publish is in progress")
                .isEqualTo(Thread.State.WAITING));
        assertThat(closeFinished.await(50, MILLISECONDS))
            .as("closeFinished latch should not count down while close is blocked")
            .isFalse();

        // allow publish to finish
        publishCanFinish.countDown();

        // close thread should now finish
        closeThread.join(5000);
        assertThat(closeFinished.await(5, SECONDS))
            .as("closeFinished latch should count down and close should complete after publish finishes")
            .isTrue();

        scheduledPublishingThread.join(5000);
    }

    @Test
    void publishSafelyOrSkipIfInProgressRespectsInterrupt() throws InterruptedException {
        CountDownLatch publishStarted = new CountDownLatch(1);
        CountDownLatch publishInterrupted = new CountDownLatch(1);

        CountingPushMeterRegistry registry = new CountingPushMeterRegistry(config, Clock.SYSTEM) {
            @Override
            protected void publish() {
                publishStarted.countDown();
                try {
                    // Block indefinitely until interrupted
                    new CountDownLatch(1).await();
                }
                catch (InterruptedException e) {
                    publishInterrupted.countDown();
                    Thread.currentThread().interrupt(); // restore interrupt status
                    return; // skip super.publish()
                }
                super.publish();
            }
        };

        // start scheduled publish but don't let it finish yet
        Thread scheduledPublishingThread = new Thread(registry::publishSafelyOrSkipIfInProgress,
                "testScheduledMetricsPublisherThread");
        scheduledPublishingThread.start();

        // wait for publish to start
        assertThat(publishStarted.await(5, SECONDS)).as("scheduled publish should start").isTrue();

        // close registry during publish
        CountDownLatch closeFinished = new CountDownLatch(1);
        Thread closeThread = new Thread(() -> {
            registry.close();
            closeFinished.countDown();
        }, "simulatedShutdownHookThread");
        closeThread.start();

        // close is blocked (waiting for publish to finish)
        assertThat(closeFinished.await(50, MILLISECONDS))
            .as("closeFinished latch should not count down while close is blocked")
            .isFalse();

        // interrupt the publishing thread
        scheduledPublishingThread.interrupt();

        // publish thread should detect the interrupt
        assertThat(publishInterrupted.await(5, SECONDS))
            .as("publishing thread should detect the interrupt and count down publishInterrupted")
            .isTrue();

        // both threads should finish
        scheduledPublishingThread.join(5000);
        closeThread.join(5000);

        assertThat(closeFinished.await(5, SECONDS))
            .as("closeFinished latch should count down and close should complete after publishing thread is interrupted")
            .isTrue();
        assertThat(registry.publishCount.get()).as("publish should not be completed successfully").isZero();
    }

    @Test
    void closeRespectsInterrupt() throws InterruptedException {
        CountDownLatch publishStarted = new CountDownLatch(1);
        CountDownLatch publishFinished = new CountDownLatch(1);

        CountingPushMeterRegistry registry = new CountingPushMeterRegistry(config, Clock.SYSTEM) {
            @Override
            protected void publish() {
                publishStarted.countDown();
                try {
                    if (!publishFinished.await(5, SECONDS)) {
                        throw new RuntimeException("Timeout waiting for publish to finish");
                    }
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                super.publish();
            }
        };

        // start scheduled publish but don't let it finish yet
        Thread scheduledPublishingThread = new Thread(registry::publishSafelyOrSkipIfInProgress,
                "testScheduledMetricsPublisherThread");
        scheduledPublishingThread.start();

        // wait for publish to start
        assertThat(publishStarted.await(5, SECONDS)).as("scheduled publish should start and block inside publish()")
            .isTrue();

        // close registry during publish
        CountDownLatch closeFinished = new CountDownLatch(1);
        Thread closeThread = new Thread(() -> {
            registry.close();
            closeFinished.countDown();
        }, "simulatedShutdownHookThread");
        closeThread.start();

        // close is blocked (waiting for publish to finish)
        assertThat(closeFinished.await(50, MILLISECONDS))
            .as("closeFinished latch should not count down while close is blocked")
            .isFalse();

        // interrupt the close thread
        closeThread.interrupt();

        // close thread should finish immediately without waiting for publish to finish
        closeThread.join(5000);
        assertThat(closeFinished.await(5, SECONDS))
            .as("closeFinished latch should count down and close should complete immediately after closeThread is interrupted")
            .isTrue();

        // publish thread should still be blocked (has not finished yet)
        assertThat(registry.publishCount.get()).as("publish should not be completed successfully yet").isZero();

        // clean up the scheduled publishing thread immediately
        scheduledPublishingThread.interrupt();
        scheduledPublishingThread.join(5000);
    }

    static class CountingPushMeterRegistry extends PushMeterRegistry {

        AtomicInteger publishCount = new AtomicInteger();

        protected CountingPushMeterRegistry(PushRegistryConfig config, Clock clock) {
            super(config, clock);
        }

        @Override
        protected <T> Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> valueFunction) {
            return null;
        }

        @Override
        protected Counter newCounter(Meter.Id id) {
            return null;
        }

        @Override
        protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
                PauseDetector pauseDetector) {
            return null;
        }

        @Override
        protected DistributionSummary newDistributionSummary(Meter.Id id,
                DistributionStatisticConfig distributionStatisticConfig, double scale) {
            return null;
        }

        @Override
        protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
            return null;
        }

        @Override
        protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction,
                ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
            return null;
        }

        @Override
        protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
            return null;
        }

        @Override
        protected TimeUnit getBaseTimeUnit() {
            return null;
        }

        @Override
        protected DistributionStatisticConfig defaultHistogramConfig() {
            return null;
        }

        @Override
        protected void publish() {
            publishCount.incrementAndGet();
        }

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
