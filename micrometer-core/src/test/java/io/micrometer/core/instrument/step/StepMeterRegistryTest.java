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
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.util.concurrent.AtomicDouble;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * Tests for {@link StepMeterRegistry}.
 *
 * @author Jon Schneider
 * @author Samuel Cox
 * @author Johnny Lim
 */
class StepMeterRegistryTest {

    private AtomicInteger publishes = new AtomicInteger();

    private MockClock clock = new MockClock();

    private StepRegistryConfig config = new StepRegistryConfig() {
        @Override
        public String prefix() {
            return "test";
        }

        @Override
        public String get(String key) {
            return null;
        }
    };

    private MyStepMeterRegistry registry = new MyStepMeterRegistry();

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
        assertThat(publishes.get()).isEqualTo(0);
        registry.close();
        assertThat(publishes.get()).isEqualTo(1);
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
        assertThat(counter.count()).isZero();
        assertThat(timer.count()).isZero();
        assertThat(timer.totalTime(MILLISECONDS)).isZero();
        assertThat(summary.count()).isZero();
        assertThat(summary.totalAmount()).isZero();
        assertThat(functionCounter.count()).isZero();
        assertThat(functionTimer.count()).isZero();
        assertThat(functionTimer.totalTime(MILLISECONDS)).isZero();

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
        assertThat(counter.count()).isZero();
        assertThat(timer.count()).isZero();
        assertThat(timer.totalTime(MILLISECONDS)).isZero();
        assertThat(summary.count()).isZero();
        assertThat(summary.totalAmount()).isZero();
        assertThat(functionCounter.count()).isZero();
        assertThat(functionTimer.count()).isZero();
        assertThat(functionTimer.totalTime(MILLISECONDS)).isZero();

        clock.add(config.step());
        registry.publish();

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
        clock.add(config.step().dividedBy(2));
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
        assertThat(counter.count()).isZero();
        assertThat(timer.count()).isZero();
        assertThat(timer.totalTime(MILLISECONDS)).isZero();
        assertThat(summary.count()).isZero();
        assertThat(summary.totalAmount()).isZero();
        assertThat(functionCounter.count()).isZero();
        assertThat(functionTimer.count()).isZero();
        assertThat(functionTimer.totalTime(MILLISECONDS)).isZero();

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
        Gauge.builder("gauge", () -> 12).register(registry);

        // before rollover
        assertThat(counter.count()).isZero();
        assertThat(timer.count()).isZero();
        assertThat(timer.totalTime(MILLISECONDS)).isZero();
        assertThat(summary.count()).isZero();
        assertThat(summary.totalAmount()).isZero();
        assertThat(functionCounter.count()).isZero();
        assertThat(functionTimer.count()).isZero();
        assertThat(functionTimer.totalTime(MILLISECONDS)).isZero();

        clock.addSeconds(60);
        // simulate this being scheduled at the start of the step
        registry.pollMetersToRollover();

        clock.addSeconds(1);
        // these recordings belong to the current step and should not be published
        counter.increment();
        timer.record(5, MILLISECONDS);
        summary.record(8);
        clock.addSeconds(10);

        // recordings that happened in the previous step should be published
        registry.publish();
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

    private class MyStepMeterRegistry extends StepMeterRegistry {

        Deque<Double> publishedCounterCounts = new ArrayDeque<>();

        Deque<Long> publishedTimerCounts = new ArrayDeque<>();

        Deque<Double> publishedTimerSumMilliseconds = new ArrayDeque<>();

        Deque<Long> publishedSummaryCounts = new ArrayDeque<Long>();

        Deque<Double> publishedSummaryTotals = new ArrayDeque<>();

        Deque<Double> publishedFunctionCounterCounts = new ArrayDeque<>();

        Deque<Double> publishedFunctionTimerCounts = new ArrayDeque<>();

        Deque<Double> publishedFunctionTimerTotals = new ArrayDeque<>();

        @Nullable
        Runnable prePublishAction;

        MyStepMeterRegistry() {
            super(StepMeterRegistryTest.this.config, StepMeterRegistryTest.this.clock);
        }

        void setPrePublishAction(Runnable prePublishAction) {
            this.prePublishAction = prePublishAction;
        }

        @Override
        protected void publish() {
            if (prePublishAction != null) {
                prePublishAction.run();
            }
            publishes.incrementAndGet();
            getMeters().stream()
                .map(meter -> meter.match(g -> null, this::publishCounter, this::publishTimer, this::publishSummary,
                        null, tg -> null, this::publishFunctionCounter, this::publishFunctionTimer, m -> null))
                .collect(Collectors.toList());
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

        @Override
        protected TimeUnit getBaseTimeUnit() {
            return TimeUnit.SECONDS;
        }

    }

}
