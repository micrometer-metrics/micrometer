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

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
        public Duration step() {
            return Duration.ofSeconds(1);
        }

        @Override
        public String prefix() {
            return "test";
        }

        @Override
        public String get(String key) {
            return null;
        }
    };

    private MeterRegistry registry = new StepMeterRegistry(config, clock) {
        @Override
        protected void publish() {
            publishes.incrementAndGet();
        }

        @Override
        protected TimeUnit getBaseTimeUnit() {
            return TimeUnit.SECONDS;
        }
    };

    @Issue("#370")
    @Test
    void serviceLevelObjectivesOnlyNoPercentileHistogram() {
        DistributionSummary summary = DistributionSummary.builder("my.summary").serviceLevelObjectives(1.0, 2)
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

    @Issue("#1882")
    @Test
    void shouldPublishCompleteMetricsOnClose() {
        // given
        final String timerName = "my.timer";
        final MutableQuadruple<Long, Double, Double, Double> timerValues =
                new MutableQuadruple<>(0L, 0.0, 0.0, 0.0);
        final StepMeterRegistry localRegistry = new StepMeterRegistry(config, Clock.SYSTEM) {
            @Override
            protected void publish() {
                publishes.incrementAndGet();
                final Timer timer = timer(timerName);
                timerValues.one = timer.count();
                timerValues.two = timer.totalTime(getBaseTimeUnit());
                timerValues.three = timer.mean(getBaseTimeUnit());
                timerValues.four = timer.max(getBaseTimeUnit());
            }

            @Override
            protected TimeUnit getBaseTimeUnit() {
                return MILLISECONDS;
            }
        };
        localRegistry.start(Executors.defaultThreadFactory());
        final Timer timer = Timer.builder(timerName).register(localRegistry);
        timer.record(ofMillis(100));
        timer.record(ofMillis(200));

        // when
        localRegistry.close();

        // then
        assertSoftly(softly -> {
            softly.assertThat(publishes.get()).isEqualTo(1);
            softly.assertThat(timerValues.one).isEqualTo(2);
            softly.assertThat(timerValues.two).isEqualTo(300.0);
            softly.assertThat(timerValues.three).isEqualTo(150.0);
            softly.assertThat(timerValues.four).isEqualTo(200.0);
        });
    }

    @Issue("#1993")
    @Test
    void timerMaxValueDecays() {
        Timer timerStep1Length2 = Timer.builder("timer1x2").distributionStatisticBufferLength(2)
                .distributionStatisticExpiry(config.step()).register(registry);

        Timer timerStep2Length2 = Timer.builder("timer2x2").distributionStatisticBufferLength(2)
                .distributionStatisticExpiry(config.step().multipliedBy(2)).register(registry);

        Timer timerStep1Length6 = Timer.builder("timer1x6").distributionStatisticBufferLength(6)
                .distributionStatisticExpiry(config.step()).register(registry);

        List<Timer> timers = Arrays.asList(timerStep1Length2, timerStep2Length2, timerStep1Length6);

        timers.forEach(t -> t.record(ofMillis(15)));

        assertSoftly(softly -> {
            softly.assertThat(timerStep1Length2.max(MILLISECONDS)).isEqualTo(15L);
            softly.assertThat(timerStep2Length2.max(MILLISECONDS)).isEqualTo(15L);
            softly.assertThat(timerStep1Length6.max(MILLISECONDS)).isEqualTo(15L);
        });

        clock.add(config.step().plus(ofMillis(1)));
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

    private static class MutableQuadruple<T1, T2, T3, T4> {
        T1 one;
        T2 two;
        T3 three;
        T4 four;

        public MutableQuadruple(T1 one, T2 two, T3 three, T4 four) {
            this.one = one;
            this.two = two;
            this.three = three;
            this.four = four;
        }
    }
}
