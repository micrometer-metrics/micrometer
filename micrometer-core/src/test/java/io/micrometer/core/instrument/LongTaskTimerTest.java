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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static io.micrometer.core.instrument.MockClock.clock;

class LongTaskTimerTest {

    @DisplayName("total time is preserved for a single timing")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void record(MeterRegistry registry) {
        LongTaskTimer t = registry.longTaskTimer("myTimer");
        long tId = t.start();
        clock(registry).addAndGetNanos(10);

        assertAll(() -> assertEquals(10, t.duration()),
                () -> assertEquals(10, t.duration(tId)),
                () -> assertEquals(1, t.activeTasks()));

        clock(registry).addAndGetNanos(10);
        t.stop(tId);

        assertAll(() -> assertEquals(0, t.duration()),
                () -> assertEquals(-1, t.duration(tId)),
                () -> assertEquals(0, t.activeTasks()));
    }
}
