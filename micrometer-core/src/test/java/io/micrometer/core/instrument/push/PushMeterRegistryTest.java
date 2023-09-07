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
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

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
import static org.mockito.ArgumentMatchers.any;

/**
 * Tests for {@link PushMeterRegistry}.
 */
class PushMeterRegistryTest {

    static final Duration STEP_DURATION = Duration.ofMillis(10);
    static ThreadFactory threadFactory = new NamedThreadFactory("PushMeterRegistryTest");

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

    class OverlappingStepRegistry extends StepMeterRegistry {

        private final AtomicInteger numExports = new AtomicInteger(0);

        private final List<Double> measurements = new ArrayList<>();

        private final CyclicBarrier alignPublishStartBarrier;

        private final CyclicBarrier publishFinishedBarrier;

        private final StepRegistryConfig config;

        public OverlappingStepRegistry(StepRegistryConfig config, Clock clock, CyclicBarrier alignPublishStartBarrier,
                CyclicBarrier publishFinishedBarrier) {
            super(config, clock);
            this.config = config;
            this.alignPublishStartBarrier = alignPublishStartBarrier;
            this.publishFinishedBarrier = publishFinishedBarrier;
        }

        @Override
        protected TimeUnit getBaseTimeUnit() {
            return SECONDS;
        }

        @Override
        protected void publish() {
            try {
                alignPublishStartBarrier.await(config.step().toMillis(), MILLISECONDS);

                // ensure publish is still running while close is running:
                Thread.sleep(config.step().dividedBy(2).toMillis());

            }
            catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                throw new RuntimeException(e);
            }

            // do the actual "publishing", i.e. adding the measurements to a list in this
            // case.
            measurements.clear();
            getMeters().stream()
                .forEach(meter -> meter.measure().forEach(measurement -> measurements.add(measurement.getValue())));

            numExports.incrementAndGet();
            try {
                publishFinishedBarrier.await(config.step().toMillis(), MILLISECONDS);
            }
            catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }

        public List<Double> getMeasurements() {
            return ImmutableList.copyOf(measurements);
        }

        public int getNumExports() {
            return numExports.get();
        }

    }

    @Test
    void scheduledPublishInterruptedByCloseWillDropData_whenShutdownTimeoutIsZero()
            throws InterruptedException, BrokenBarrierException, TimeoutException {

        StepRegistryConfig config = new StepRegistryConfig() {
            @Override
            public Duration step() {
                // need a longer step duration, as there is a lot of stuff happening
                // between exports and tests get a bit flaky with a step size of 10ms
                // (lots of waiting etc.)
                return Duration.ofMillis(200);
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
            public Duration overlappingShutdownWaitTimeout() {
                // this is the default for step registry, will keep the current behavior
                return Duration.ZERO;
            }
        };

        MockClock clock = new MockClock();
        CyclicBarrier publishStartedBarrier = new CyclicBarrier(2);
        CyclicBarrier publishFinishedBarrier = new CyclicBarrier(2);
        Offset<Double> tolerance = Offset.offset(0.001);

        OverlappingStepRegistry registry = new OverlappingStepRegistry(config, clock, publishStartedBarrier,
                publishFinishedBarrier);
        registry.start(threadFactory);

        // first export cycle starts here

        Counter counter = registry.counter("counter");
        counter.increment(3);

        // before the first export no values exported by stepmeter
        assertThat(registry.getMeasurements()).isEmpty();
        assertThat(registry.getNumExports()).isZero();

        // # first export
        clock.add(config.step());
        // wait until publish has started from the timed invocation.
        publishStartedBarrier.await(config.step().toMillis(), MILLISECONDS);

        // second export cycle starts here

        // wait for the publish to finish. Ensures values are processed when retrieving
        // them from the registry
        publishFinishedBarrier.await(config.step().toMillis(), MILLISECONDS);

        assertThat(registry.getNumExports()).isOne();
        assertThat(registry.getMeasurements()).hasSize(1);
        assertThat(registry.getMeasurements().get(0)).isCloseTo(3, tolerance);

        clock.add(config.step().dividedBy(2));
        counter.increment(4);
        clock.add(config.step().dividedBy(2));

        // wait until the second publish starts
        publishStartedBarrier.await(config.step().toMillis(), MILLISECONDS);

        // third export cycle starts here, since we waited for the publishStartedBarrier
        // above we know that publishing is in progress

        // close registry while export is still running. When close returns, the
        // application exits.
        registry.close();

        // value has not yet rolled over, and we export the value from the previous export
        // and only 1 export finished
        // THIS IS A STALE STATE, the last export was not finished.
        assertThat(registry.getNumExports()).isOne();
        assertThat(registry.getMeasurements()).hasSize(1);
        assertThat(registry.getMeasurements().get(0)).isCloseTo(3, tolerance);

        // This would not happen when registry.close() is called, as the app will wait for
        // waits for close() to finish, then shut down immediately.
        // In this test, we can see that it _would_ fix itself if we waited for the
        // already in-progress publish to finish.
        publishFinishedBarrier.await(config.step().toMillis(), MILLISECONDS);

        assertThat(registry.getNumExports()).isEqualTo(2);
        assertThat(registry.getMeasurements()).hasSize(1);
        assertThat(registry.getMeasurements().get(0)).isCloseTo(4, tolerance);
    }

    @Test
    void scheduledPublishInterruptedByCloseWillNotDropData_whenShutdownTimeoutIsBigEnough()
            throws InterruptedException, BrokenBarrierException, TimeoutException {

        StepRegistryConfig config = new StepRegistryConfig() {
            @Override
            public Duration step() {
                // need a longer step duration, as there is a lot of stuff happening
                // between exports and tests get a bit flaky with a step size of 10ms
                // (lots of waiting etc.)
                return Duration.ofMillis(200);
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
            public Duration overlappingShutdownWaitTimeout() {
                // wait for up to 500 millis for the scheduled export to finish.
                return Duration.ofMillis(500);
            }
        };

        MockClock clock = new MockClock();
        CyclicBarrier publishStartedBarrier = new CyclicBarrier(2);
        CyclicBarrier publishFinishedBarrier = new CyclicBarrier(2);
        Offset<Double> tolerance = Offset.offset(0.001);

        OverlappingStepRegistry registry = new OverlappingStepRegistry(config, clock, publishStartedBarrier,
                publishFinishedBarrier);
        registry.start(threadFactory);

        // first export cycle starts here

        Counter counter = registry.counter("counter");
        counter.increment(3);

        // before the first export no values exported by stepmeter
        assertThat(registry.getMeasurements()).isEmpty();
        assertThat(registry.getNumExports()).isZero();

        // # first export
        clock.add(config.step());
        // wait until publish has started from the timed invocation.
        publishStartedBarrier.await(config.step().toMillis(), MILLISECONDS);

        // second export cycle starts here

        // wait for the publish to finish. Ensures values are processed when retrieving
        // them from the registry
        publishFinishedBarrier.await(config.step().toMillis(), MILLISECONDS);

        assertThat(registry.getNumExports()).isOne();
        assertThat(registry.getMeasurements()).hasSize(1);
        assertThat(registry.getMeasurements().get(0)).isCloseTo(3, tolerance);

        clock.add(config.step().dividedBy(2));
        counter.increment(4);
        clock.add(config.step().dividedBy(2));

        // wait until the second publish starts
        publishStartedBarrier.await(config.step().toMillis(), MILLISECONDS);

        // third export cycle starts here, since we waited for the publishStartedBarrier
        // above we know that publishing is in progress

        // ensure we don't wait for the publishFinished barrier in the publish method.
        // In close, we are waiting for publish to finish, but publish will only finish
        // once the barrier has released. Therefore, we need to ensure the barrier will
        // release as soon as it is hit in publish.
        // assert that at this point we haven't yet hit the barrier in the publish method
        // (zero waiting threads).
        assertThat(publishFinishedBarrier.getNumberWaiting()).isZero();

        // make sure that the barrier in publish will be cleared when it's hit, by waiting
        // for it in a background thread.
        ExecutorService waitForExportBarrier = Executors.newSingleThreadExecutor();
        waitForExportBarrier.submit(() -> {
            try {
                publishFinishedBarrier.await(config.step().toMillis(), MILLISECONDS);
            }
            catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        });

        // export is still in progress, number of finished exports is one (the previous
        // export) at this point.
        assertThat(registry.getNumExports()).isOne();

        // close registry while export is still running. When close returns, the
        // application exits. Since the overlappingShutdownWaitTimeout is large enough,
        // registry.close() will wait until the scheduled export is finished.
        registry.close();

        // After waiting for the export to finish in close, the data is not stale, and the
        // values from the last export cycle have been exported:
        assertThat(registry.getNumExports()).isEqualTo(2);
        assertThat(registry.getMeasurements()).hasSize(1);
        assertThat(registry.getMeasurements().get(0)).isCloseTo(4, tolerance);
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
        // myThreadFactory.join();

        assertThat(overlappingStepMeterRegistry.publishes).as("only one publish happened").hasSize(1);
        Deque<Double> firstPublishValues = overlappingStepMeterRegistry.publishes.get(0);
        assertThat(firstPublishValues.pop()).isEqualTo(1);
        assertThat(firstPublishValues.pop()).isEqualTo(2.5);
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
            assertThat(delay).isBetween(minOffsetMillis, maxOffsetMillis - 1);
            observedDelays.add(delay);
        });
        List<Long> expectedDelays = LongStream.range(minOffsetMillis, maxOffsetMillis)
            .boxed()
            .collect(Collectors.toList());
        assertThat(observedDelays).containsExactlyElementsOf(expectedDelays);
    }

    private static class OverlappingStepMeterRegistry extends StepMeterRegistry {

        private final AtomicInteger numberOfPublishes = new AtomicInteger();

        private final Map<Integer, Deque<Double>> publishes = new ConcurrentHashMap<>();

        private final CyclicBarrier barrier;

        OverlappingStepMeterRegistry(StepRegistryConfig config, Clock clock, CyclicBarrier barrier) {
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
            int publishIndex = numberOfPublishes.getAndIncrement();
            getMeters().stream()
                .filter(meter -> meter instanceof Counter)
                .map(meter -> (Counter) meter)
                .forEach(counter -> publishes.merge(publishIndex, new ArrayDeque<>(Arrays.asList(counter.count())),
                        (l1, l2) -> {
                            l1.addAll(l2);
                            return l1;
                        }));
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
