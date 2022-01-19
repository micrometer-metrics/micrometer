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
package io.micrometer.api.instrument.step;

import io.micrometer.api.Issue;
import io.micrometer.api.instrument.Meter;
import io.micrometer.api.instrument.MockClock;
import io.micrometer.api.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.api.instrument.distribution.pause.PauseDetector;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

class StepTimerTest {
    @Issue("#1814")
    @Test
    void meanShouldWorkIfTotalTimeNotCalled() {
        Duration stepDuration = Duration.ofMillis(10);
        MockClock clock = new MockClock();
        StepTimer timer = new StepTimer(
                mock(Meter.Id.class),
                clock,
                DistributionStatisticConfig.builder().expiry(stepDuration).bufferLength(2).build(),
                mock(PauseDetector.class),
                TimeUnit.MILLISECONDS,
                stepDuration.toMillis(),
                false
        );

        clock.add(stepDuration);
        assertThat(timer.mean(TimeUnit.MILLISECONDS)).isEqualTo(0.0);

        clock.add(Duration.ofMillis(1));
        timer.record(Duration.ofMillis(50));
        timer.record(Duration.ofMillis(100));

        clock.add(stepDuration);
        assertThat(timer.mean(TimeUnit.MILLISECONDS)).isEqualTo(75.0);
    }
}
