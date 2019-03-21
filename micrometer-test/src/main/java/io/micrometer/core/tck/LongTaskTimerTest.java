/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.core.tck;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static io.micrometer.core.instrument.MockClock.clock;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

interface LongTaskTimerTest {

    @Test
    @DisplayName("total time is preserved for a single timing")
    default void record(MeterRegistry registry) {
        LongTaskTimer t = registry.more().longTaskTimer("myTimer");

        LongTaskTimer.Sample sample = t.start();
        clock(registry).add(10, TimeUnit.NANOSECONDS);

        assertAll(() -> assertEquals(10, t.duration(TimeUnit.NANOSECONDS)),
            () -> assertEquals(0.01, t.duration(TimeUnit.MICROSECONDS)),
            () -> assertEquals(10, sample.duration(TimeUnit.NANOSECONDS)),
            () -> assertEquals(0.01, sample.duration(TimeUnit.MICROSECONDS)),
            () -> assertEquals(1, t.activeTasks()));

        clock(registry).add(10, TimeUnit.NANOSECONDS);
        sample.stop();

        assertAll(() -> assertEquals(0, t.duration(TimeUnit.NANOSECONDS)),
            () -> assertEquals(-1, sample.duration(TimeUnit.NANOSECONDS)),
            () -> assertEquals(0, t.activeTasks()));
    }
}
