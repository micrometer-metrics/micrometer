/*
 * Copyright 2020 VMware, Inc.
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
import io.micrometer.core.instrument.MockClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class StepValueTest {

    private final MockClock clock = new MockClock();

    @Test
    void poll() {
        final AtomicLong aLong = new AtomicLong(42);
        final long stepTime = 60;

        final StepValue<Long> stepValue = new StepValue<Long>(clock, stepTime) {
            @Override
            public Supplier<Long> valueSupplier() {
                return () -> aLong.getAndSet(0);
            }

            @Override
            public Long noValue() {
                return 0L;
            }
        };

        assertThat(stepValue.poll()).isEqualTo(0L);

        clock.add(Duration.ofMillis(1));
        assertThat(stepValue.poll()).isEqualTo(0L);

        clock.add(Duration.ofMillis(59));
        assertThat(stepValue.poll()).isEqualTo(42L);

        clock.add(Duration.ofMillis(60));
        assertThat(stepValue.poll()).isEqualTo(0L);

        clock.add(Duration.ofMillis(60));
        assertThat(stepValue.poll()).isEqualTo(0L);

        aLong.set(24);
        assertThat(stepValue.poll()).isEqualTo(0L);

        clock.add(Duration.ofMillis(60));
        assertThat(stepValue.poll()).isEqualTo(24L);

        clock.add(Duration.ofMillis(stepTime / 2));
        aLong.set(27);
        assertThat(stepValue.poll()).isEqualTo(24L);

        stepValue._closingRollover();
        assertThat(stepValue.poll()).isEqualTo(27L);
    }

    @Test
    @Issue("#3720")
    void closingRolloverShouldNotDropDataOnStepCompletion() {
        final MockClock clock = new MockClock();
        final long stepTime = 60;
        final AtomicLong aLong = new AtomicLong(10);
        final StepValue<Long> stepValue = new StepValue<Long>(clock, stepTime) {
            @Override
            public Supplier<Long> valueSupplier() {
                return () -> aLong.getAndSet(0);
            }

            @Override
            public Long noValue() {
                return 0L;
            }
        };
        clock.add(Duration.ofMillis(1));
        assertThat(stepValue.poll()).isZero();
        clock.add(Duration.ofMillis(59));
        assertThat(stepValue.poll()).isEqualTo(10);
        clock.add(Duration.ofMillis(stepTime - 1));
        aLong.set(5);
        stepValue._closingRollover();
        assertThat(stepValue.poll()).isEqualTo(5);
        clock.add(Duration.ofMillis(1));
        assertThat(stepValue.poll()).isEqualTo(5L);
        clock.add(stepTime, TimeUnit.MILLISECONDS);
        assertThat(stepValue.poll()).isEqualTo(5L);
    }

}
