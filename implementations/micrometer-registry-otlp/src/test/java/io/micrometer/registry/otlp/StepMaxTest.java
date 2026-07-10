/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.registry.otlp;

import io.micrometer.core.instrument.MockClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class StepMaxTest {

    private final Duration step = Duration.ofMillis(10);

    MockClock clock;

    @BeforeEach
    void init() {
        clock = new MockClock();
    }

    @Test
    void testMax() {
        StepMax max = new StepMax(clock, step.toMillis());

        assertThat(max.poll()).isZero();
        max.record(10.0);
        assertThat(max.poll()).isZero();
        clock.add(step);
        assertThat(max.poll()).isEqualTo(10);
        max.record(1.0);
        max.record(11);
        clock.add(Duration.ofMillis(5));
        assertThat(max.poll()).isEqualTo(10);
        clock.add(Duration.ofMillis(5));
        assertThat(max.poll()).isEqualTo(11);
        clock.add(step);
        assertThat(max.poll()).isZero();
    }

}
