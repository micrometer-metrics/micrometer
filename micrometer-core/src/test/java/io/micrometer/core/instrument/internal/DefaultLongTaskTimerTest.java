/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.core.instrument.internal;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer.SampleImpl;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer.SampleImplCounted;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultLongTaskTimerTest {

    private MockClock clock;

    private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        clock = new MockClock();
        registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    }

    @Test
    void timestampCollisionShouldBeOk() {
        LongTaskTimer ltt = LongTaskTimer.builder("my.timer").register(registry);
        LongTaskTimer.Sample sample1 = ltt.start();
        LongTaskTimer.Sample sample2 = ltt.start();
        assertThat(sample1).isInstanceOf(SampleImpl.class).isNotInstanceOf(SampleImplCounted.class);
        assertThat(sample2).isInstanceOf(SampleImplCounted.class).isNotSameAs(sample1);

        SampleImpl sampleImpl1 = (SampleImpl) sample1;
        SampleImpl sampleImpl2 = (SampleImpl) sample2;
        clock.addSeconds(1);
        assertThat(sample1.duration(TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(sample2.duration(TimeUnit.SECONDS)).isEqualTo(1);

        LongTaskTimer.Sample sample3 = ltt.start();
        LongTaskTimer.Sample sample4 = ltt.start();
        assertThat(sample3).isInstanceOf(SampleImpl.class).isNotInstanceOf(SampleImplCounted.class);
        assertThat(sample4).isInstanceOf(SampleImplCounted.class);

        SampleImpl sampleImpl3 = (SampleImpl) sample3;
        SampleImpl sampleImpl4 = (SampleImpl) sample4;
        assertThat(ltt.activeTasks()).isEqualTo(4);
        assertThat(sampleImpl4).isEqualByComparingTo(sampleImpl4)
            .isGreaterThan(sampleImpl3)
            .isGreaterThan(sampleImpl2)
            .isGreaterThan(sampleImpl1);
        assertThat(sampleImpl3).isEqualByComparingTo(sampleImpl3)
            .isLessThan(sampleImpl4)
            .isGreaterThan(sampleImpl2)
            .isGreaterThan(sampleImpl1);
        assertThat(sampleImpl2).isEqualByComparingTo(sampleImpl2)
            .isLessThan(sampleImpl4)
            .isLessThan(sampleImpl3)
            .isGreaterThan(sampleImpl1);
        assertThat(sampleImpl1).isEqualByComparingTo(sampleImpl1)
            .isLessThan(sampleImpl4)
            .isLessThan(sampleImpl3)
            .isLessThan(sampleImpl2);

        assertThat(sample2.stop()).isEqualTo(TimeUnit.SECONDS.toNanos(1));
        assertThat(ltt.activeTasks()).isEqualTo(3);
        assertThat(sample3.stop()).isEqualTo(TimeUnit.SECONDS.toNanos(0));
        assertThat(ltt.activeTasks()).isEqualTo(2);
        assertThat(sample4.stop()).isEqualTo(TimeUnit.SECONDS.toNanos(0));
        assertThat(ltt.activeTasks()).isEqualTo(1);
        assertThat(sample1.stop()).isEqualTo(TimeUnit.SECONDS.toNanos(1));
        assertThat(ltt.activeTasks()).isEqualTo(0);
    }

    @Test
    void counterShouldSurviveOverflow() {
        LongTaskTimer ltt = LongTaskTimer.builder("my.timer").register(registry);
        assertThat(ltt).isInstanceOf(DefaultLongTaskTimer.class);
        assertInternalCounterIsZero(ltt.start());

        ((DefaultLongTaskTimer) ltt).setCounter(Integer.MAX_VALUE - 2);
        assertInternalCounterValue(ltt.start(), Integer.MAX_VALUE - 1);
        assertInternalCounterValue(ltt.start(), Integer.MAX_VALUE);
        assertInternalCounterValue(ltt.start(), Integer.MIN_VALUE);
        assertInternalCounterValue(ltt.start(), Integer.MIN_VALUE + 1);
    }

    @Test
    void counterShouldJumpZero() {
        LongTaskTimer ltt = LongTaskTimer.builder("my.timer").register(registry);
        assertThat(ltt).isInstanceOf(DefaultLongTaskTimer.class);
        assertInternalCounterIsZero(ltt.start());

        ((DefaultLongTaskTimer) ltt).setCounter(-2);
        assertInternalCounterValue(ltt.start(), -1);
        assertInternalCounterValue(ltt.start(), 1);
    }

    private void assertInternalCounterIsZero(LongTaskTimer.Sample sample) {
        assertThat(sample).isNotInstanceOf(SampleImplCounted.class)
            .isInstanceOfSatisfying(SampleImpl.class, si -> assertThat(si.counter()).isZero());
    }

    private void assertInternalCounterValue(LongTaskTimer.Sample sample, int expected) {
        assertThat(sample).isInstanceOfSatisfying(SampleImplCounted.class,
                sic -> assertThat(sic.counter()).isEqualTo(expected));
    }

}
