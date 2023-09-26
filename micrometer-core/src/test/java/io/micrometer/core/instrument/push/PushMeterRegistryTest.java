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

        private final CyclicBarrier publishStartBarrier;

        private final CyclicBarrier publishWorkBarrier;

        private final CyclicBarrier publishFinishedBarrier;

        private final StepRegistryConfig config;

        public OverlappingStepRegistry(StepRegistryConfig config, Clock clock, CyclicBarrier publishStartBarrier,
                CyclicBarrier publishWorkBarrier, CyclicBarrier publishFinishedBarrier) {
            super(config, clock);
            this.config = config;
            this.publishStartBarrier = publishStartBarrier;
            this.publishWorkBarrier = publishWorkBarrier;
            this.publishFinishedBarrier = publishFinishedBarrier;
        }

        @Override
        protected TimeUnit getBaseTimeUnit() {
            return SECONDS;
        }

        @Override
        protected void publish() {
            try {
                // allows us to block before starting any work (simulated or otherwise)
                publishStartBarrier.await();
                // simulate publish working until we're ready to let it proceed
                publishWorkBarrier.await();
            }
            catch (InterruptedException | BrokenBarrierException e) {
                throw new RuntimeException(e);
            }

            // do the actual "publishing", i.e. adding the measurements to a list in this
            // case.
            measurements.clear();
            getMeters()
                .forEach(meter -> meter.measure().forEach(measurement -> measurements.add(measurement.getValue())));

            numExports.incrementAndGet();
            try {
                publishFinishedBarrier.await();
            }
            catch (InterruptedException | BrokenBarrierException e) {
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
    @Issue("#3872")
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

        // Timeout for synchronizing threads needs to be longer than the step time.
        var barrierTimeoutMillis = 500;

        MockClock clock = new MockClock();
        CyclicBarrier publishStartedBarrier = new CyclicBarrier(2);
        CyclicBarrier publishFinishedBarrier = new CyclicBarrier(2);
        CyclicBarrier publishWorkBarrier = new CyclicBarrier(2);
        Offset<Double> tolerance = Offset.offset(0.001);

        OverlappingStepRegistry registry = new OverlappingStepRegistry(config, clock, publishStartedBarrier,
                publishWorkBarrier, publishFinishedBarrier);
        registry.start(threadFactory);

        // data collection for the first export cycle starts here

        Counter counter = registry.counter("counter");
        counter.increment(3);

        // before the first export no values exported by stepmeter
        assertThat(registry.getMeasurements()).isEmpty();
        assertThat(registry.getNumExports()).isZero();

        // first export: everything goes according to plan, no overlaps.
        clock.add(config.step());
        // wait until publish has started from the timed invocation.
        publishStartedBarrier.await(barrierTimeoutMillis, MILLISECONDS);
        // artificial work finishes.
        publishWorkBarrier.await(barrierTimeoutMillis, MILLISECONDS);
        // wait for the publish to finish. Ensures values are processed when retrieving
        // them from the registry
        publishFinishedBarrier.await(barrierTimeoutMillis, MILLISECONDS);

        // data collection for the second export cycle starts here.
        // before continuing to the second export, assert on data from the first cycle:
        assertThat(registry.getNumExports()).isOne();
        assertThat(registry.getMeasurements()).hasSize(1);
        assertThat(registry.getMeasurements().get(0)).isCloseTo(3, tolerance);

        // record data in the second export interval
        clock.add(config.step().dividedBy(2));
        counter.increment(4);
        clock.add(config.step().dividedBy(2));

        // second export: will be interrupted by a shutdown signal
        // wait until the second publish starts
        publishStartedBarrier.await(barrierTimeoutMillis, MILLISECONDS);

        // data collection for the third export cycle starts here.
        // Since we waited for the publishStartedBarrier above, we know that publishing is
        // in progress

        // close registry while export is still running. When close returns, the
        // application exits.
        // The overlapping duration timeout is zero in this case, so close will return
        // immediately, since publishing is already in progress.
        registry.close();

        // value has not yet rolled over, and the last exported data is from the previous
        // export.
        // THIS IS A STALE STATE, the last export was not finished and the final exported
        // state is "old" data.
        assertThat(registry.getNumExports()).isOne();
        assertThat(registry.getMeasurements()).hasSize(1);
        assertThat(registry.getMeasurements().get(0)).isCloseTo(3, tolerance);

        // artificial work finishes
        publishWorkBarrier.await(barrierTimeoutMillis, MILLISECONDS);

        // This would not happen when registry.close() is called, as the app will wait for
        // close() to finish, then shut down immediately.
        // In this test, we can see that it _would_ fix itself if we waited for the
        // already in-progress publish to finish.
        publishFinishedBarrier.await(barrierTimeoutMillis, MILLISECONDS);

        assertThat(registry.getNumExports()).isEqualTo(2);
        assertThat(registry.getMeasurements()).hasSize(1);
        assertThat(registry.getMeasurements().get(0)).isCloseTo(4, tolerance);
    }

    @Test
    @Issue("#3872")
    void scheduledPublishInterruptedByCloseWillNotDropData_whenShutdownTimeoutIsBigEnough()
            throws InterruptedException, BrokenBarrierException, TimeoutException, ExecutionException {

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

        // Timeout for synchronizing threads needs to be longer than the step time and
        // shutdown timeout.
        var barrierTimeoutMillis = 600;

        MockClock clock = new MockClock();
        CyclicBarrier publishStartedBarrier = new CyclicBarrier(2);
        CyclicBarrier publishWorkBarrier = new CyclicBarrier(2);
        CyclicBarrier publishFinishedBarrier = new CyclicBarrier(2);
        Offset<Double> tolerance = Offset.offset(0.001);

        OverlappingStepRegistry registry = new OverlappingStepRegistry(config, clock, publishStartedBarrier,
                publishWorkBarrier, publishFinishedBarrier);
        registry.start(threadFactory);

        // data collection for first export cycle starts here.

        Counter counter = registry.counter("counter");
        counter.increment(3);

        // before the first export no values exported by stepmeter
        assertThat(registry.getMeasurements()).isEmpty();
        assertThat(registry.getNumExports()).isZero();

        // first export: no overlap, everything works as expected.
        clock.add(config.step());

        // wait until publish has started from the timed invocation.
        publishStartedBarrier.await(barrierTimeoutMillis, MILLISECONDS);
        // do not do any artificial work.
        publishWorkBarrier.await(barrierTimeoutMillis, MILLISECONDS);

        // wait for the first publish to finish. Ensures values are processed when
        // retrieving them from the registry
        publishFinishedBarrier.await(barrierTimeoutMillis, MILLISECONDS);

        // data collection for the second export cycle starts
        // First assert on the data exported in the first cycle:

        assertThat(registry.getNumExports()).isOne();
        assertThat(registry.getMeasurements()).hasSize(1);
        assertThat(registry.getMeasurements().get(0)).isCloseTo(3, tolerance);

        // add data for the second export
        clock.add(config.step().dividedBy(2));
        counter.increment(4);
        clock.add(config.step().dividedBy(2));

        // second publish: will be interrupted by the shutdown signal, but will wait for
        // the export to finish since the timeout is big enough.
        // wait until the second publish starts
        publishStartedBarrier.await(barrierTimeoutMillis, MILLISECONDS);
        // data collection for the third export cycle starts here.

        // close registry while export is still running. When close returns, the
        // application exits. Since the overlappingShutdownWaitTimeout is large enough,
        // registry.close() will wait until the scheduled export is finished.
        assertThat(registry.isPublishing()).isTrue();
        // Run close from a background thread, to ensure this is happening at the same
        // time as the export already in progress:
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(registry::close);
        var assertionFuture = executor.submit(() -> {
            // After waiting for the export to finish in close, the data is not stale, and
            // the values from the last export cycle have been exported. If we'd just
            // re-publish again in close the application may have exited already before
            // the running export has finished.
            assertThat(registry.getNumExports()).isEqualTo(2);
            assertThat(registry.getMeasurements()).hasSize(1);
            assertThat(registry.getMeasurements().get(0)).isCloseTo(4, tolerance);
        });

        // Wait a bit to ensure that we don't let the export finish before the
        // PushMeterRegistry.close() knows that we need to wait for export, then
        // let simulated work finish let publish return.
        Thread.sleep(100);
        publishWorkBarrier.await(barrierTimeoutMillis, MILLISECONDS);
        publishFinishedBarrier.await(barrierTimeoutMillis, MILLISECONDS);

        // bubble up any exceptions we got from the assertion future.
        assertionFuture.get();
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
