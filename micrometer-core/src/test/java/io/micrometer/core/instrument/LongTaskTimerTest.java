/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.concurrent.TimeUnit;

import static io.micrometer.core.instrument.MockClock.clock;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LongTaskTimerTest {

    @DisplayName("total time is preserved for a single timing")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void record(MeterRegistry registry) {
        LongTaskTimer t = registry.more().longTaskTimer(registry.createId("myTimer", emptyList(), null));

        long tId = t.start();
        clock(registry).addAndGetNanos(10);

        assertAll(() -> assertEquals(10, t.duration(TimeUnit.NANOSECONDS)),
            () -> assertEquals(0.01, t.duration(TimeUnit.MICROSECONDS)),
            () -> assertEquals(10, t.duration(tId, TimeUnit.NANOSECONDS)),
            () -> assertEquals(0.01, t.duration(tId, TimeUnit.MICROSECONDS)),
            () -> assertEquals(1, t.activeTasks()));

        clock(registry).addAndGetNanos(10);
        t.stop(tId);

        assertAll(() -> assertEquals(0, t.duration(TimeUnit.NANOSECONDS)),
            () -> assertEquals(-1, t.duration(tId, TimeUnit.NANOSECONDS)),
            () -> assertEquals(0, t.activeTasks()));
    }
}
