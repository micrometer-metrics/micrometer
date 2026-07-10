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
package io.micrometer.core.instrument.composite;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeLongTaskTimerTest {

    @Test
    void mapIdsToEachLongTaskTimerInComposite() {
        MockClock clock = new MockClock();
        MeterRegistry s1 = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
        LongTaskTimer anotherTimer = s1.more().longTaskTimer("long.task");

        LongTaskTimer.Sample anotherSample = anotherTimer.start();
        clock.add(10, TimeUnit.NANOSECONDS);

        CompositeMeterRegistry registry = new CompositeMeterRegistry(clock);
        registry.add(s1);

        LongTaskTimer longTaskTimer = registry.more().longTaskTimer("long.task");
        LongTaskTimer.Sample sample = longTaskTimer.start();

        clock.add(100, TimeUnit.NANOSECONDS);
        assertThat(anotherSample.stop()).isEqualTo(110);

        assertThat(sample.stop()).isEqualTo(100);
    }

}
