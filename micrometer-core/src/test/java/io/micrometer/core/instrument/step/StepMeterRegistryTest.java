/*
 * Copyright 2017 VMware, Inc.
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

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.util.concurrent.AtomicDouble;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

/**
 * Tests for {@link StepMeterRegistry}.
 *
 * @author Jon Schneider
 * @author Samuel Cox
 * @author Johnny Lim
 */
class StepMeterRegistryTest {

    private final MockClock clock = new MockClock();

    private final StepRegistryConfig config = new StepRegistryConfig() {
        @Override
        public String prefix() {
            return "test";
        }

        @Override
        public String get(String key) {
            return null;
        }
    };

    private final MyStepMeterRegistry registry = new MyStepMeterRegistry();

    @Issue("#370")
    @Test
    void serviceLevelObjectivesOnlyNoPercentileHistogram() {
        DistributionSummary summary = DistributionSummary.builder("my.summary")
            .serviceLevelObjectives(1.0, 2)
            .register(registry);

        summary.record(1);

        Timer timer = Timer.builder("my.timer").serviceLevelObjectives(ofMillis(1)).register(registry);
        timer.record(1, MILLISECONDS);

        Gauge summaryHist1 = registry.get("my.summary.histogram").tags("le", "1").gauge();
        Gauge summaryHist2 = registry.get("my.summary.histogram").tags("le", "2").gauge();
        Gauge timerHist = registry.get("my.timer.histogram").tags("le", "0.001").gauge();

        assertThat(summaryHist1.value()).isEqualTo(1);
        assertThat(summaryHist2.value()).isEqualTo(1);
        assertThat(timerHist.value()).isEqualTo(1);

        clock.add(config.step());

        assertThat(summaryHist1.value()).isEqualTo(0);
        assertThat(summaryHist2.value()).isEqualTo(0);
        assertThat(timerHist.value()).isEqualTo(0);
    }

    @Issue("#484")
    @Test
    void publishOneLastTimeOnClose() {
        assertThat(registry.publishCount.get()).isZero();
        registry.close();
        assertThat(registry.publishCount.get()).isEqualTo(1);
    }

    @Issue("#1993")
    @Test
    void timerMaxValueDecays() {
        Timer timerStep1Length2 = Timer.builder("timer1x2")
            .distributionStatisticBufferLength(2)
            .distributionStatisticExpiry(config.step())
            .register(registry);

        Timer timerStep2Length2 = Timer.builder("timer2x2")
            .distributionStatisticBufferLength(2)
            .distributionStatisticExpiry(config.step().multipliedBy(2))
            .register(registry);

        Timer timerStep1Length6 = Timer.builder("timer1x6")
            .distributionStatisticBufferLength(6)
            .distributionStatisticExpiry(config.step())
            .register(registry);

        List<Timer> timers = Arrays.asList(timerStep1Length2, timerStep2Length2, timerStep1Length6);

        timers.forEach(t -> t.record(ofMillis(15)));

        assertSoftly(softly -> {
            softly.assertThat(timerStep1Length2.max(MILLISECONDS)).isEqualTo(15L);
            softly.assertThat(timerStep2Length2.max(MILLISECONDS)).isEqualTo(15L);
            softly.assertThat(timerStep1Length6.max(MILLISECONDS)).isEqualTo(15L);
        });

        clock.add(config.step().plus(Duration.ofMillis(1)));
        clock.add(config.step());
        timers.forEach(t -> t.record(ofMillis(10)));

        assertSoftly(softly -> {
            softly.assertThat(timerStep1Length2.max(MILLISECONDS)).isEqualTo(10L);
            softly.assertThat(timerStep2Length2.max(MILLISECONDS)).isEqualTo(15L);
            softly.assertThat(timerStep1Length6.max(MILLISECONDS)).isEqualTo(15L);
        });

        clock.add(config.step());
        timers.forEach(t -> t.record(ofMillis(5)));

        assertSoftly(softly -> {
            softly.assertThat(timerStep1Length2.max(MILLISECONDS)).isEqualTo(10L);
            softly.assertThat(timerStep2Length2.max(MILLISECONDS)).isEqualTo(15L);
            softly.assertThat(timerStep1Length6.max(MILLISECONDS)).isEqualTo(15L);
        });

        clock.add(config.step());
        assertSoftly(softly -> {
            softly.assertThat(timerStep1Length2.max(MILLISECONDS)).isEqualTo(5L);
            softly.assertThat(timerStep2Length2.max(MILLISECONDS)).isEqualTo(10L);
            softly.assertThat(timerStep1Length6.max(MILLISECONDS)).isEqualTo(15L);
        });

        clock.add(config.step().multipliedBy(5));
        assertSoftly(softly -> {
            softly.assertThat(timerStep1Length2.max(MILLISECONDS)).isEqualTo(0L);
            softly.assertThat(timerStep2Length2.max(MILLISECONDS)).isEqualTo(0L);
            softly.assertThat(timerStep1Length6.max(MILLISECONDS)).isEqualTo(0L);
        });
    }

    @Issue("#1882")
    @Test
    void shortLivedPublish() {
        Counter counter = Counter.builder("counter").register(registry);
        counter.increment();
        Timer timer = Timer.builder("timer").register(registry);
        timer.record(5, MILLISECONDS);
        DistributionSummary summary = DistributionSummary.builder("summary").register(registry);
        summary.record(7);
        FunctionCounter functionCounter = FunctionCounter.builder("counter.function", this, obj -> 15)
            .register(registry);
        FunctionTimer functionTimer = FunctionTimer.builder("timer.function", this, obj -> 3, obj -> 53, MILLISECONDS)
            .register(registry);

        // before step rollover
        assertBeforeRollover(counter, timer, summary, functionCounter, functionTimer);

        registry.close();

        assertThat(registry.publishedCounterCounts).hasSize(1);
        assertThat(registry.publishedCounterCounts.pop()).isOne();
        assertThat(registry.publishedTimerCounts).hasSize(1);
        assertThat(registry.publishedTimerCounts.pop()).isOne();
        assertThat(registry.publishedTimerSumMilliseconds).hasSize(1);
        assertThat(registry.publishedTimerSumMilliseconds.pop()).isEqualTo(5.0);
        assertThat(registry.publishedSummaryCounts).hasSize(1);
        assertThat(registry.publishedSummaryCounts.pop()).isOne();
        assertThat(registry.publishedSummaryTotals).hasSize(1);
        assertThat(registry.publishedSummaryTotals.pop()).isEqualTo(7);
        assertThat(registry.publishedFunctionCounterCounts).hasSize(1);
        assertThat(registry.publishedFunctionCounterCounts.pop()).isEqualTo(15);
        assertThat(registry.publishedFunctionTimerCounts).hasSize(1);
        assertThat(registry.publishedFunctionTimerCounts.pop()).isEqualTo(3);
        assertThat(registry.publishedFunctionTimerTotals).hasSize(1);
        assertThat(registry.publishedFunctionTimerTotals.pop()).isEqualTo(53);
    }

    @Issue("#1882")
    @Test
    void finalPushHasPartialStep() {
        AtomicDouble counterCount = new AtomicDouble(15);
        AtomicLong timerCount = new AtomicLong(3);
        AtomicDouble timerTotalTime = new AtomicDouble(53);

        Counter counter = Counter.builder("counter").register(registry);
        counter.increment();
        Timer timer = Timer.builder("timer").register(registry);
        timer.record(5, MILLISECONDS);
        DistributionSummary summary = DistributionSummary.builder("summary").register(registry);
        summary.record(7);
        FunctionCounter functionCounter = FunctionCounter.builder("counter.function", this, obj -> counterCount.get())
            .register(registry);
        FunctionTimer functionTimer = FunctionTimer
            .builder("timer.function", this, obj -> timerCount.get(), obj -> timerTotalTime.get(), MILLISECONDS)
            .register(registry);

        // before step rollover
        assertBeforeRollover(counter, timer, summary, functionCounter, functionTimer);

        addTimeWithRolloverOnStepStart(clock, registry, config, config.step());
        registry.scheduledPublish();
        registry.waitForInProgressScheduledPublish();

        assertThat(registry.publishedCounterCounts).hasSize(1);
        assertThat(registry.publishedCounterCounts.pop()).isOne();
        assertThat(registry.publishedTimerCounts).hasSize(1);
        assertThat(registry.publishedTimerCounts.pop()).isOne();
        assertThat(registry.publishedTimerSumMilliseconds).hasSize(1);
        assertThat(registry.publishedTimerSumMilliseconds.pop()).isEqualTo(5.0);
        assertThat(registry.publishedSummaryCounts).hasSize(1);
        assertThat(registry.publishedSummaryCounts.pop()).isOne();
        assertThat(registry.publishedSummaryTotals).hasSize(1);
        assertThat(registry.publishedSummaryTotals.pop()).isEqualTo(7);
        assertThat(registry.publishedFunctionCounterCounts).hasSize(1);
        assertThat(registry.publishedFunctionCounterCounts.pop()).isEqualTo(15);
        assertThat(registry.publishedFunctionTimerCounts).hasSize(1);
        assertThat(registry.publishedFunctionTimerCounts.pop()).isEqualTo(3);
        assertThat(registry.publishedFunctionTimerTotals).hasSize(1);
        assertThat(registry.publishedFunctionTimerTotals.pop()).isEqualTo(53);

        // set clock to middle of second step
        addTimeWithRolloverOnStepStart(clock, registry, config, config.step().dividedBy(2));
        // record some more values in new step interval
        counter.increment(2);
        timer.record(6, MILLISECONDS);
        summary.record(8);
        counterCount.set(18);
        timerCount.set(5);
        timerTotalTime.set(77);

        assertThat(registry.publishedCounterCounts).isEmpty();
        assertThat(registry.publishedTimerCounts).isEmpty();
        assertThat(registry.publishedTimerSumMilliseconds).isEmpty();
        assertThat(registry.publishedSummaryCounts).isEmpty();
        assertThat(registry.publishedSummaryTotals).isEmpty();
        assertThat(registry.publishedFunctionCounterCounts).isEmpty();
        assertThat(registry.publishedFunctionTimerCounts).isEmpty();
        assertThat(registry.publishedFunctionTimerTotals).isEmpty();

        // shutdown
        registry.close();

        assertThat(registry.publishedCounterCounts).hasSize(1);
        assertThat(registry.publishedTimerCounts).hasSize(1);
        assertThat(registry.publishedTimerSumMilliseconds).hasSize(1);
        assertThat(registry.publishedSummaryCounts).hasSize(1);
        assertThat(registry.publishedSummaryTotals).hasSize(1);
        assertThat(registry.publishedFunctionCounterCounts).hasSize(1);
        assertThat(registry.publishedFunctionTimerCounts).hasSize(1);
        assertThat(registry.publishedFunctionTimerTotals).hasSize(1);

        assertThat(registry.publishedCounterCounts.pop()).isEqualTo(2);
        assertThat(registry.publishedTimerCounts.pop()).isEqualTo(1);
        assertThat(registry.publishedTimerSumMilliseconds.pop()).isEqualTo(6.0);
        assertThat(registry.publishedSummaryCounts.pop()).isOne();
        assertThat(registry.publishedSummaryTotals.pop()).isEqualTo(8);
        assertThat(registry.publishedFunctionCounterCounts.pop()).isEqualTo(3);
        assertThat(registry.publishedFunctionTimerCounts.pop()).isEqualTo(2);
        assertThat(registry.publishedFunctionTimerTotals.pop()).isEqualTo(24);
    }

    @Issue("#3720")
    @Test
    void publishOnCloseCrossesStepBoundary() {
        Counter counter = Counter.builder("counter").register(registry);
        counter.increment();
        Timer timer = Timer.builder("timer").register(registry);
        timer.record(5, MILLISECONDS);
        DistributionSummary summary = DistributionSummary.builder("summary").register(registry);
        summary.record(7);
        FunctionCounter functionCounter = FunctionCounter.builder("counter.function", this, obj -> 15)
            .register(registry);
        FunctionTimer functionTimer = FunctionTimer.builder("timer.function", this, obj -> 3, obj -> 53, MILLISECONDS)
            .register(registry);

        // before rollover
        assertBeforeRollover(counter, timer, summary, functionCounter, functionTimer);

        // before publishing, simulate a step boundary being crossed after forced rollover
        // on close and before/during publishing
        registry.setPrePublishAction(() -> clock.add(config.step()));
        // force rollover and publish on close
        registry.close();

        assertThat(registry.publishedCounterCounts).hasSize(1);
        assertThat(registry.publishedCounterCounts.pop()).isOne();
        assertThat(registry.publishedTimerCounts).hasSize(1);
        assertThat(registry.publishedTimerCounts.pop()).isOne();
        assertThat(registry.publishedTimerSumMilliseconds).hasSize(1);
        assertThat(registry.publishedTimerSumMilliseconds.pop()).isEqualTo(5.0);
        assertThat(registry.publishedSummaryCounts).hasSize(1);
        assertThat(registry.publishedSummaryCounts.pop()).isOne();
        assertThat(registry.publishedSummaryTotals).hasSize(1);
        assertThat(registry.publishedSummaryTotals.pop()).isEqualTo(7);
        assertThat(registry.publishedFunctionCounterCounts).hasSize(1);
        assertThat(registry.publishedFunctionCounterCounts.pop()).isEqualTo(15);
        assertThat(registry.publishedFunctionTimerCounts).hasSize(1);
        assertThat(registry.publishedFunctionTimerCounts.pop()).isEqualTo(3);
        assertThat(registry.publishedFunctionTimerTotals).hasSize(1);
        assertThat(registry.publishedFunctionTimerTotals.pop()).isEqualTo(53);
    }

    @Test
    @Issue("#3863")
    void shouldPublishLastCompletedStepWhenClosingBeforeScheduledPublish() {
        Counter counter = Counter.builder("counter_3863").register(registry);
        Timer timer = Timer.builder("timer_3863").register(registry);
        DistributionSummary summary = DistributionSummary.builder("summary_3863").register(registry);

        AtomicLong functionValue = new AtomicLong(0);
        FunctionCounter functionCounter = FunctionCounter
            .builder("counter.function_3863", functionValue, AtomicLong::get)
            .register(registry);
        FunctionTimer functionTimer = FunctionTimer
            .builder("timer.function_3863", this, obj -> 3, obj -> 53, MILLISECONDS)
            .register(registry);

        counter.increment();
        timer.record(5, MILLISECONDS);
        summary.record(5);
        functionValue.set(1);

        // before rollover
        assertBeforeRollover(counter, timer, summary, functionCounter, functionTimer);

        addTimeWithRolloverOnStepStart(clock, registry, config, Duration.ofSeconds(60));

        // All new recordings now belong to next step.
        counter.increment(2);
        timer.record(10, MILLISECONDS);
        summary.record(10);
        functionValue.incrementAndGet();

        // Simulating the application close behaviour before actual publishing happens.
        registry.close();

        assertThat(registry.publishedCounterCounts).hasSize(2);
        assertThat(registry.sumAllPublishedValues(registry.publishedCounterCounts)).isEqualTo(3);
        assertThat(registry.publishedTimerCounts).hasSize(2);
        assertThat(registry.sumAllPublishedValues(registry.publishedTimerCounts)).isEqualTo(2);
        assertThat(registry.sumAllPublishedValues(registry.publishedTimerSumMilliseconds)).isEqualTo(15);
        assertThat(registry.publishedSummaryCounts).hasSize(2);
        assertThat(registry.sumAllPublishedValues(registry.publishedSummaryCounts)).isEqualTo(2);
        assertThat(registry.sumAllPublishedValues(registry.publishedSummaryTotals)).isEqualTo(15);

        assertThat(registry.publishedFunctionCounterCounts).hasSize(2);
        assertThat(registry.sumAllPublishedValues(registry.publishedFunctionCounterCounts)).isEqualTo(2);

        assertThat(registry.publishedFunctionTimerCounts).hasSize(2);
        assertThat(registry.sumAllPublishedValues(registry.publishedFunctionTimerCounts)).isEqualTo(3);
        assertThat(registry.sumAllPublishedValues(registry.publishedFunctionTimerTotals)).isEqualTo(53);
    }

    @Test
    @Issue("#2818")
    void scheduledRollOver() {
        Counter counter = Counter.builder("counter").register(registry);
        counter.increment();
        Timer timer = Timer.builder("timer").register(registry);
        timer.record(5, MILLISECONDS);
        DistributionSummary summary = DistributionSummary.builder("summary").register(registry);
        summary.record(7);
        FunctionCounter functionCounter = FunctionCounter.builder("counter.function", this, obj -> 15)
            .register(registry);
        FunctionTimer functionTimer = FunctionTimer.builder("timer.function", this, obj -> 3, obj -> 53, MILLISECONDS)
            .register(registry);

        // before rollover
        assertBeforeRollover(counter, timer, summary, functionCounter, functionTimer);

        addTimeWithRolloverOnStepStart(clock, registry, config, Duration.ofSeconds(60));
        // these recordings belong to the current step and should not be published
        counter.increment();
        timer.record(5, MILLISECONDS);
        summary.record(8);
        addTimeWithRolloverOnStepStart(clock, registry, config, Duration.ofSeconds(10));

        // recordings that happened in the previous step should be published
        registry.scheduledPublish();
        registry.waitForInProgressScheduledPublish();

        assertThat(registry.publishedCounterCounts).hasSize(1);
        assertThat(registry.publishedCounterCounts.pop()).isOne();
        assertThat(registry.publishedTimerCounts).hasSize(1);
        assertThat(registry.publishedTimerCounts.pop()).isOne();
        assertThat(registry.publishedTimerSumMilliseconds).hasSize(1);
        assertThat(registry.publishedTimerSumMilliseconds.pop()).isEqualTo(5.0);
        assertThat(registry.publishedSummaryCounts).hasSize(1);
        assertThat(registry.publishedSummaryCounts.pop()).isOne();
        assertThat(registry.publishedSummaryTotals).hasSize(1);
        assertThat(registry.publishedSummaryTotals.pop()).isEqualTo(7);
        assertThat(registry.publishedFunctionCounterCounts).hasSize(1);
        assertThat(registry.publishedFunctionCounterCounts.pop()).isEqualTo(15);
        assertThat(registry.publishedFunctionTimerCounts).hasSize(1);
        assertThat(registry.publishedFunctionTimerCounts.pop()).isEqualTo(3);
        assertThat(registry.publishedFunctionTimerTotals).hasSize(1);
        assertThat(registry.publishedFunctionTimerTotals.pop()).isEqualTo(53);
    }

    @Test
    @Issue("#3914")
    void publishShouldNotHappenWhenRegistryIsDisabled() {
        StepRegistryConfig disabledStepRegistryConfig = new StepRegistryConfig() {
            @Override
            public String prefix() {
                return "test";
            }

            @Override
            public boolean enabled() {
                return false;
            }

            @Nullable
            @Override
            public String get(String key) {
                return null;
            }
        };

        MyStepMeterRegistry disabledStepMeterRegistry = new MyStepMeterRegistry(disabledStepRegistryConfig, clock);
        Counter.builder("publish_disabled_counter").register(disabledStepMeterRegistry).increment();

        clock.add(config.step());
        assertThat(disabledStepMeterRegistry.publishCount.get()).isZero();
        disabledStepMeterRegistry.close();
        assertThat(disabledStepMeterRegistry.publishCount.get()).isZero();
    }

    @Test
    @Issue("#3914")
    void publishShouldNotHappenWhenRegistryIsClosed() {
        Counter.builder("my.counter").register(registry).increment();

        clock.add(config.step());
        assertThat(registry.publishCount.get()).isZero();
        registry.close();
        assertThat(registry.publishCount.get()).isEqualTo(1);
        assertThat(registry.publishedCounterCounts).hasSize(1);

        clock.add(config.step());
        registry.close();
        assertThat(registry.publishCount.get()).isEqualTo(1);
        assertThat(registry.publishedCounterCounts).hasSize(1);
    }

    @Test
    @Issue("gh-3846")
    void whenCloseDuringScheduledPublish_thenPreviousStepAndCurrentPartialStepArePublished()
            throws InterruptedException {
        AtomicDouble counterCount = new AtomicDouble(15);
        AtomicLong timerCount = new AtomicLong(3);
        AtomicDouble timerTotalTime = new AtomicDouble(53);

        Counter counter = Counter.builder("counter").register(registry);
        counter.increment();
        Timer timer = Timer.builder("timer").register(registry);
        timer.record(5, MILLISECONDS);
        DistributionSummary summary = DistributionSummary.builder("summary").register(registry);
        summary.record(7);
        FunctionCounter functionCounter = FunctionCounter.builder("counter.function", this, obj -> counterCount.get())
            .register(registry);
        FunctionTimer functionTimer = FunctionTimer
            .builder("timer.function", this, obj -> timerCount.get(), obj -> timerTotalTime.get(), MILLISECONDS)
            .register(registry);

        // before step rollover
        assertBeforeRollover(counter, timer, summary, functionCounter, functionTimer);

        addTimeWithRolloverOnStepStart(clock, registry, config, config.step());

        // set clock to middle of second step
        addTimeWithRolloverOnStepStart(clock, registry, config, config.step().dividedBy(2));
        // record some more values in new step interval
        counter.increment(2);
        timer.record(6, MILLISECONDS);
        summary.record(8);
        counterCount.set(18);
        timerCount.set(5);
        timerTotalTime.set(77);

        // close registry during scheduled publish
        CountDownLatch latch = new CountDownLatch(1);
        registry.scheduledPublish(() -> {
            try {
                latch.await();
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        await().pollDelay(1, MILLISECONDS)
            .atMost(100, MILLISECONDS)
            .untilAsserted(() -> assertThat(registry.isPublishing()).isTrue());
        Thread closeThread = new Thread(registry::close, "simulatedShutdownHookThread");
        closeThread.start();
        latch.countDown();
        closeThread.join();

        // publish happened twice - scheduled publish of first step and closing publish of
        // partial second step
        assertThat(registry.publishedCounterCounts).hasSize(2);
        assertThat(registry.publishedTimerCounts).hasSize(2);
        assertThat(registry.publishedTimerSumMilliseconds).hasSize(2);
        assertThat(registry.publishedSummaryCounts).hasSize(2);
        assertThat(registry.publishedSummaryTotals).hasSize(2);
        assertThat(registry.publishedFunctionCounterCounts).hasSize(2);
        assertThat(registry.publishedFunctionTimerCounts).hasSize(2);
        assertThat(registry.publishedFunctionTimerTotals).hasSize(2);

        // first (full) step
        assertThat(registry.publishedCounterCounts.pop()).isOne();
        assertThat(registry.publishedTimerCounts.pop()).isOne();
        assertThat(registry.publishedTimerSumMilliseconds.pop()).isEqualTo(5.0);
        assertThat(registry.publishedSummaryCounts.pop()).isOne();
        assertThat(registry.publishedSummaryTotals.pop()).isEqualTo(7);
        assertThat(registry.publishedFunctionCounterCounts.pop()).isEqualTo(15);
        assertThat(registry.publishedFunctionTimerCounts.pop()).isEqualTo(3);
        assertThat(registry.publishedFunctionTimerTotals.pop()).isEqualTo(53);

        // second step (partial)
        assertThat(registry.publishedCounterCounts.pop()).isEqualTo(2);
        assertThat(registry.publishedTimerCounts.pop()).isEqualTo(1);
        assertThat(registry.publishedTimerSumMilliseconds.pop()).isEqualTo(6.0);
        assertThat(registry.publishedSummaryCounts.pop()).isOne();
        assertThat(registry.publishedSummaryTotals.pop()).isEqualTo(8);
        assertThat(registry.publishedFunctionCounterCounts.pop()).isEqualTo(3);
        assertThat(registry.publishedFunctionTimerCounts.pop()).isEqualTo(2);
        assertThat(registry.publishedFunctionTimerTotals.pop()).isEqualTo(24);
    }

    @Test
    @Issue("#4357")
    void publishOnceWhenClosedWithinFirstStep() {
        // Set the initial clock time to a valid time.
        MockClock mockClock = new MockClock();
        mockClock.add(config.step().multipliedBy(5));

        MyStepMeterRegistry stepMeterRegistry = new MyStepMeterRegistry(config, mockClock);
        assertThat(stepMeterRegistry.publishCount.get()).isZero();
        stepMeterRegistry.close();
        assertThat(stepMeterRegistry.publishCount.get()).isEqualTo(1);
    }

    @Test
    void startWithNamedThreadFactoryShouldUseNamedThreadFactoryForPoller() {
        StepMeterRegistry registry = new CustomStepMeterRegistry();
        String publisherThreadName = "custom-metrics-publisher";
        registry.start(new NamedThreadFactory(publisherThreadName));
        List<String> threadNames = Thread.getAllStackTraces()
            .keySet()
            .stream()
            .map((thread) -> thread.getName())
            .collect(Collectors.toList());
        assertThat(threadNames).contains(publisherThreadName)
            .doesNotContain(publisherThreadName + "-2")
            .contains("step-meter-registry-poller-for-CustomStepMeterRegistry");
    }

    static class CustomStepMeterRegistry extends StepMeterRegistry {

        CustomStepMeterRegistry() {
            super(new StepRegistryConfig() {
                @Override
                public String prefix() {
                    return null;
                }

                @Override
                public String get(String key) {
                    return null;
                }
            }, new MockClock());
        }

        @Override
        protected void publish() {
        }

        @Override
        protected TimeUnit getBaseTimeUnit() {
            return TimeUnit.SECONDS;
        }

    }

    private class MyStepMeterRegistry extends StepMeterRegistry {

        private final AtomicInteger publishCount = new AtomicInteger();

        Deque<Double> publishedCounterCounts = new ArrayDeque<>();

        Deque<Long> publishedTimerCounts = new ArrayDeque<>();

        Deque<Double> publishedTimerSumMilliseconds = new ArrayDeque<>();

        Deque<Long> publishedSummaryCounts = new ArrayDeque<Long>();

        Deque<Double> publishedSummaryTotals = new ArrayDeque<>();

        Deque<Double> publishedFunctionCounterCounts = new ArrayDeque<>();

        Deque<Double> publishedFunctionTimerCounts = new ArrayDeque<>();

        Deque<Double> publishedFunctionTimerTotals = new ArrayDeque<>();

        private long lastScheduledPublishStartTime;

        @Nullable
        Runnable prePublishAction;

        AtomicBoolean isPublishing = new AtomicBoolean(false);

        CompletableFuture<Void> scheduledPublishingFuture = CompletableFuture.completedFuture(null);

        MyStepMeterRegistry() {
            this(StepMeterRegistryTest.this.config, StepMeterRegistryTest.this.clock);
        }

        MyStepMeterRegistry(StepRegistryConfig config, Clock clock) {
            super(config, clock);
        }

        void setPrePublishAction(Runnable prePublishAction) {
            this.prePublishAction = prePublishAction;
        }

        @Override
        protected void publish() {
            if (prePublishAction != null) {
                prePublishAction.run();
            }
            publishCount.incrementAndGet();
            getMeters().stream()
                .map(meter -> meter.match(g -> null, this::publishCounter, this::publishTimer, this::publishSummary,
                        null, tg -> null, this::publishFunctionCounter, this::publishFunctionTimer, m -> null))
                .collect(Collectors.toList());
        }

        private void scheduledPublish() {
            scheduledPublish(() -> {
            });
        }

        private void scheduledPublish(Runnable prePublishRunnable) {
            scheduledPublishingFuture = CompletableFuture.runAsync(() -> {
                if (isPublishing.compareAndSet(false, true)) {
                    this.lastScheduledPublishStartTime = clock.wallTime();
                    try {
                        prePublishRunnable.run();
                        publish();
                    }
                    finally {
                        isPublishing.set(false);
                    }
                }
            });
        }

        @Override
        protected boolean isPublishing() {
            return isPublishing.get();
        }

        @Override
        protected void waitForInProgressScheduledPublish() {
            try {
                scheduledPublishingFuture.get();
            }
            catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected long getLastScheduledPublishStartTime() {
            return lastScheduledPublishStartTime;
        }

        private Timer publishTimer(Timer timer) {
            publishedTimerCounts.add(timer.count());
            publishedTimerSumMilliseconds.add(timer.totalTime(MILLISECONDS));
            return timer;
        }

        private FunctionTimer publishFunctionTimer(FunctionTimer functionTimer) {
            publishedFunctionTimerCounts.add(functionTimer.count());
            publishedFunctionTimerTotals.add(functionTimer.totalTime(MILLISECONDS));
            return functionTimer;
        }

        private Counter publishCounter(Counter counter) {
            publishedCounterCounts.add(counter.count());
            return counter;
        }

        private FunctionCounter publishFunctionCounter(FunctionCounter functionCounter) {
            publishedFunctionCounterCounts.add(functionCounter.count());
            return functionCounter;
        }

        private DistributionSummary publishSummary(DistributionSummary summary) {
            publishedSummaryCounts.add(summary.count());
            publishedSummaryTotals.add(summary.totalAmount());
            return summary;
        }

        <T extends Number> double sumAllPublishedValues(Deque<T> deque) {
            return deque.stream().mapToDouble(Number::doubleValue).sum();
        }

        @Override
        protected TimeUnit getBaseTimeUnit() {
            return TimeUnit.SECONDS;
        }

    }

    private static void assertBeforeRollover(Counter counter, Timer timer, DistributionSummary summary,
            FunctionCounter functionCounter, FunctionTimer functionTimer) {
        assertThat(counter.count()).isZero();
        assertThat(timer.count()).isZero();
        assertThat(timer.totalTime(MILLISECONDS)).isZero();
        assertThat(summary.count()).isZero();
        assertThat(summary.totalAmount()).isZero();
        assertThat(functionCounter.count()).isZero();
        assertThat(functionTimer.count()).isZero();
        assertThat(functionTimer.totalTime(MILLISECONDS)).isZero();
    }

    /**
     * This method simulates the behaviour StepRegistry will exhibit when rollOver is
     * scheduled on a thread. This calls {@link StepMeterRegistry#pollMetersToRollover()}
     * as soon as the step is crossed.
     */
    private void addTimeWithRolloverOnStepStart(MockClock clock, StepMeterRegistry registry, StepRegistryConfig config,
            Duration timeToAdd) {
        long currentTime = clock.wallTime();
        long boundaryForNextStep = ((currentTime / config.step().toMillis()) + 1) * config.step().toMillis();
        long timeToNextStep = boundaryForNextStep - currentTime;
        if (timeToAdd.toMillis() >= timeToNextStep) {
            clock.add(timeToNextStep, MILLISECONDS);
            registry.pollMetersToRollover();
            clock.add((timeToAdd.toMillis() - timeToNextStep), MILLISECONDS);
            return;
        }
        clock.add(timeToAdd);
    }

}
