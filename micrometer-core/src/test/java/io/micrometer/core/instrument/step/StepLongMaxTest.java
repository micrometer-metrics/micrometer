/**
 * Copyright 2020 Pivotal Software, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;

public class StepLongMaxTest {

    private final MockClock clock = new MockClock();

    @Test
    public void poll() {
        final long stepTime = 60;
        final StepLongMax stepMax = new StepLongMax(clock, stepTime);

        assertThat(stepMax.poll()).isEqualTo(0L);

        stepMax.record(24);
        stepMax.record(42);
        stepMax.record(4);

        clock.add(Duration.ofMillis(1));
        assertThat(stepMax.poll()).isEqualTo(0L);

        clock.add(Duration.ofMillis(59));
        assertThat(stepMax.poll()).isEqualTo(42L);

        clock.add(Duration.ofMillis(60));
        assertThat(stepMax.poll()).isEqualTo(0L);

        clock.add(Duration.ofMillis(60));
        assertThat(stepMax.poll()).isEqualTo(0L);

        stepMax.record(4);
        stepMax.record(24);

        clock.add(Duration.ofMillis(60));
        assertThat(stepMax.poll()).isEqualTo(24L);
    }
}
