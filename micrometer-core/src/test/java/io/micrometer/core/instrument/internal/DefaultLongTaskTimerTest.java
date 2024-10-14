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
import org.assertj.core.api.AbstractComparableAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactory;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultLongTaskTimerTest {

    private static final InstanceOfAssertFactory<SampleImpl, AbstractComparableAssert<?, SampleImpl>> SAMPLE_IMPL_ASSERT = new InstanceOfAssertFactory<>(
            SampleImpl.class, Assertions::assertThat);

    @Test
    void sampleTimestampCollision() {
        final MockClock clock = new MockClock();
        MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
        LongTaskTimer t = LongTaskTimer.builder("my.timer").register(registry);

        final LongTaskTimer.Sample sample1 = t.start();
        final LongTaskTimer.Sample sample2 = t.start();
        assertThat(sample1).isInstanceOf(SampleImpl.class).isNotInstanceOf(SampleImplCounted.class);
        assertThat(sample2).isInstanceOf(SampleImplCounted.class).isNotSameAs(sample1);
        final SampleImpl sampleImpl1 = (SampleImpl) sample1;
        final SampleImpl sampleImpl2 = (SampleImpl) sample2;

        clock.addSeconds(1);

        assertThat(sample1.duration(TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(sample2.duration(TimeUnit.SECONDS)).isEqualTo(1);

        final LongTaskTimer.Sample sample3 = t.start();
        final LongTaskTimer.Sample sample4 = t.start();
        assertThat(sample3).isInstanceOf(SampleImpl.class).isNotInstanceOf(SampleImplCounted.class);
        assertThat(sample4).isInstanceOf(SampleImplCounted.class);
        final SampleImpl sampleImpl3 = (SampleImpl) sample3;
        final SampleImpl sampleImpl4 = (SampleImpl) sample4;

        assertThat(t.activeTasks()).isEqualTo(4);

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
        assertThat(t.activeTasks()).isEqualTo(3);
        assertThat(sample3.stop()).isEqualTo(TimeUnit.SECONDS.toNanos(0));
        assertThat(t.activeTasks()).isEqualTo(2);
        assertThat(sample4.stop()).isEqualTo(TimeUnit.SECONDS.toNanos(0));
        assertThat(t.activeTasks()).isEqualTo(1);
        assertThat(sample1.stop()).isEqualTo(TimeUnit.SECONDS.toNanos(1));
        assertThat(t.activeTasks()).isEqualTo(0);
    }

    @Test
    void counterJumpsZeroAndWraps() {

        MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
        LongTaskTimer t = LongTaskTimer.builder("my.timer").register(registry);
        assertThat(t).isInstanceOf(DefaultLongTaskTimer.class);
        final DefaultLongTaskTimer dltt = (DefaultLongTaskTimer) t;

        dltt.setCounter(Integer.MAX_VALUE - 2);

        assertThat(t.start()).asInstanceOf(SAMPLE_IMPL_ASSERT).returns(0, SampleImpl::counter);
        assertThat(t.start()).asInstanceOf(SAMPLE_IMPL_ASSERT).returns(Integer.MAX_VALUE - 1, SampleImpl::counter);
        assertThat(t.start()).asInstanceOf(SAMPLE_IMPL_ASSERT).returns(Integer.MAX_VALUE, SampleImpl::counter);
        assertThat(t.start()).asInstanceOf(SAMPLE_IMPL_ASSERT).returns(Integer.MIN_VALUE, SampleImpl::counter);
        assertThat(t.start()).asInstanceOf(SAMPLE_IMPL_ASSERT).returns(Integer.MIN_VALUE + 1, SampleImpl::counter);

        dltt.setCounter(-2);

        assertThat(t.start()).asInstanceOf(SAMPLE_IMPL_ASSERT).returns(-1, SampleImpl::counter);
        assertThat(t.start()).asInstanceOf(SAMPLE_IMPL_ASSERT).returns(1, SampleImpl::counter);
    }

}
