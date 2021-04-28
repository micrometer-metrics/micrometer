/**
 * Copyright 2020 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.step;

import io.micrometer.core.instrument.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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
    }
}
