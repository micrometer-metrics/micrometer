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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static io.micrometer.core.instrument.MockClock.clock;
import static org.junit.jupiter.api.Assertions.*;

class TimerTest {

    @Test
    void recordThrowable() {
        MeterRegistry registry = new SimpleMeterRegistry();

        Supplier<String> timed = () -> registry.timer("timer").record(() -> "");
        timed.get();
    }

    @DisplayName("total time and count are preserved for a single timing")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void record(MeterRegistry registry) {
        Timer t = registry.timer("myTimer");
        t.record(42, TimeUnit.MILLISECONDS);
        clock(registry).addAndGet(1, TimeUnit.SECONDS);

        assertAll(() -> assertEquals(1L, t.count()),
                () -> assertEquals(42, t.totalTime(TimeUnit.MILLISECONDS), 1.0e-12));
    }

    @DisplayName("negative times are discarded by the Timer")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void recordNegative(MeterRegistry registry) {
        Timer t = registry.timer("myTimer");
        t.record(-42, TimeUnit.MILLISECONDS);

        assertAll(() -> assertEquals(0L, t.count()),
                () -> assertEquals(0, t.totalTime(TimeUnit.NANOSECONDS), 1.0e-12));
    }

    @DisplayName("zero times contribute to the count of overall events but do not add to total time")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void recordZero(MeterRegistry registry) {
        Timer t = registry.timer("myTimer");
        t.record(0, TimeUnit.MILLISECONDS);
        clock(registry).addAndGet(1, TimeUnit.SECONDS);

        assertAll(() -> assertEquals(1L, t.count()),
                () -> assertEquals(0L, t.totalTime(TimeUnit.NANOSECONDS)));
    }

    @DisplayName("record a runnable task")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void recordWithRunnable(MeterRegistry registry) throws Exception {
        Timer t = registry.timer("myTimer");

        try {
            t.record(() -> clock(registry).addAndGetNanos(10));
            clock(registry).addAndGet(1, TimeUnit.SECONDS);
        } finally {
            assertAll(() -> assertEquals(1L, t.count()),
                    () -> assertEquals(10, t.totalTime(TimeUnit.NANOSECONDS) ,1.0e-12));
        }
    }

    @DisplayName("callable task that throws exception is still recorded")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void recordCallableException(MeterRegistry registry) {
        Timer t = registry.timer("myTimer");

        assertThrows(Exception.class, () -> {
            t.recordCallable(() -> {
                clock(registry).addAndGetNanos(10);
                throw new Exception("uh oh");
            });
        });

        clock(registry).addAndGet(1, TimeUnit.SECONDS);

        assertAll(() -> assertEquals(1L, t.count()),
                () -> assertEquals(10, t.totalTime(TimeUnit.NANOSECONDS), 1.0e-12));
    }
}
