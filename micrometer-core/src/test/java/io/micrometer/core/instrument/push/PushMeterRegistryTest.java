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
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
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

    @Test
    void whenUncaughtExceptionInPublish_taskStillScheduled() throws InterruptedException {
        PushMeterRegistry pushMeterRegistry = new ThrowingPushMeterRegistry(config, latch);
        pushMeterRegistry.start(threadFactory);
        assertThat(latch.await(500, TimeUnit.MILLISECONDS))
            .as("publish should continue to be scheduled even if an uncaught exception is thrown")
            .isTrue();
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
    void scheduledPublishOverlapWithPublishOnClose() throws InterruptedException {
        MockClock clock = new MockClock();
        CyclicBarrier barrier = new CyclicBarrier(2);
        OverlappingStepMeterRegistry overlappingStepMeterRegistry = new OverlappingStepMeterRegistry(config, clock,
                barrier);
        Counter c1 = overlappingStepMeterRegistry.counter("c1");
        Counter c2 = overlappingStepMeterRegistry.counter("c2");
        c1.increment();
        c2.increment(2.5);
        clock.add(config.step());

        // simulated scheduled publish
        Thread scheduledPublishingThread = new Thread(
                () -> ((PushMeterRegistry) overlappingStepMeterRegistry).publishSafely(),
                "scheduledMetricsPublisherThread");
        scheduledPublishingThread.start();
        // publish on shutdown
        Thread onClosePublishThread = new Thread(overlappingStepMeterRegistry::close, "shutdownHookThread");
        onClosePublishThread.start();
        scheduledPublishingThread.join();
        onClosePublishThread.join();

        assertThat(overlappingStepMeterRegistry.publishes).as("only one publish happened").hasSize(1);
        Deque<Double> firstPublishValues = overlappingStepMeterRegistry.publishes.get(0);
        assertThat(firstPublishValues.pop()).isEqualTo(1);
        assertThat(firstPublishValues.pop()).isEqualTo(2.5);
    }

    private static class OverlappingStepMeterRegistry extends StepMeterRegistry {

        private final AtomicInteger numberOfPublish = new AtomicInteger();

        private final Map<Integer, Deque<Double>> publishes = new ConcurrentHashMap<>();

        private final CyclicBarrier barrier;

        public OverlappingStepMeterRegistry(StepRegistryConfig config, Clock clock, CyclicBarrier barrier) {
            super(config, clock);
            this.barrier = barrier;
        }

        @Override
        protected TimeUnit getBaseTimeUnit() {
            return SECONDS;
        }

        @Override
        protected void publish() {
            try {
                barrier.await(100, MILLISECONDS);
            }
            catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                throw new RuntimeException(e);
            }
            int publishIndex = numberOfPublish.getAndIncrement();
            for (Counter counter : getMeters().stream()
                .filter(meter -> meter instanceof Counter)
                .map(meter -> (Counter) meter)
                .collect(Collectors.toSet())) {
                publishes.merge(publishIndex, new ArrayDeque<>(Arrays.asList(counter.count())), (l1, l2) -> {
                    l1.addAll(l2);
                    return l1;
                });
            }
        }

        @Override
        public void close() {
            try {
                barrier.await(100, MILLISECONDS);
            }
            catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                throw new RuntimeException(e);
            }
            super.close();
        }

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
