/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.tck;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static io.micrometer.core.instrument.MockClock.clock;
import static io.micrometer.core.instrument.util.TimeUtils.millisToUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link Timer}
 *
 * @author Jon Schneider
 * @author Oleksii Bondar
 */
interface TimerTest {
    Duration step();

    @DisplayName("record throwables")
    @Test
    default void recordThrowable() {
        MeterRegistry registry = new SimpleMeterRegistry();

        Supplier<String> timed = () -> registry.timer("timer").record(() -> "");
        timed.get();
    }

    @Test
    @DisplayName("total time and count are preserved for a single timing")
    default void record(MeterRegistry registry) {
        Timer t = registry.timer("myTimer");
        t.record(42, TimeUnit.MILLISECONDS);
        clock(registry).add(step());

        assertAll(() -> assertEquals(1L, t.count()),
                () -> assertEquals(42, t.totalTime(TimeUnit.MILLISECONDS), 1.0e-12));
    }

    @Test
    @DisplayName("record durations")
    default void recordDuration(MeterRegistry registry) {
        Timer t = registry.timer("myTimer");
        t.record(Duration.ofMillis(42));
        clock(registry).add(step());

        assertAll(() -> assertEquals(1L, t.count()),
                () -> assertEquals(42, t.totalTime(TimeUnit.MILLISECONDS), 1.0e-12));
    }

    @Test
    @DisplayName("negative times are discarded by the Timer")
    default void recordNegative(MeterRegistry registry) {
        Timer t = registry.timer("myTimer");
        t.record(-42, TimeUnit.MILLISECONDS);

        assertAll(() -> assertEquals(0L, t.count()),
                () -> assertEquals(0, t.totalTime(TimeUnit.NANOSECONDS), 1.0e-12));
    }

    @Test
    @DisplayName("zero times contribute to the count of overall events but do not add to total time")
    default void recordZero(MeterRegistry registry) {
        Timer t = registry.timer("myTimer");
        t.record(0, TimeUnit.MILLISECONDS);
        clock(registry).add(step());

        assertAll(() -> assertEquals(1L, t.count()),
                () -> assertEquals(0L, t.totalTime(TimeUnit.NANOSECONDS)));
    }

    @Test
    @DisplayName("record a runnable task")
    default void recordWithRunnable(MeterRegistry registry) {
        Timer t = registry.timer("myTimer");

        Runnable r = () -> {
            clock(registry).add(10, TimeUnit.NANOSECONDS);
        };
        try {
            t.record(r);
            clock(registry).add(step());
        } finally {
            assertAll(() -> assertEquals(1L, t.count()),
                    () -> assertEquals(10, t.totalTime(TimeUnit.NANOSECONDS), 1.0e-12));
        }
    }

    @Test
    @DisplayName("record supplier")
    default void recordWithSupplier(MeterRegistry registry) {
        Timer t = registry.timer("myTimer");
        String expectedResult = "response";
        Supplier<String> supplier = () -> {
            clock(registry).add(10, TimeUnit.NANOSECONDS);
            return expectedResult;
        };
        try {
            String supplierResult = t.record(supplier);
            assertEquals(expectedResult, supplierResult);
            clock(registry).add(step());
        } finally {
            assertAll(() -> assertEquals(1L, t.count()),
                    () -> assertEquals(10, t.totalTime(TimeUnit.NANOSECONDS), 1.0e-12));
        }
    }
    
    @Test
    @DisplayName("wrap supplier")
    default void wrapSupplier(MeterRegistry registry) {
        Timer timer = registry.timer("myTimer");
        String expectedResult = "response";
        Supplier<String> supplier = () -> {
            clock(registry).add(10, TimeUnit.NANOSECONDS);
            return expectedResult;
        };
        try {
            Supplier<String> wrappedSupplier = timer.wrap(supplier);
            assertEquals(expectedResult, wrappedSupplier.get());
            clock(registry).add(step());
        } finally {
            assertAll(() -> assertEquals(1L, timer.count()),
                    () -> assertEquals(10, timer.totalTime(TimeUnit.NANOSECONDS), 1.0e-12));
        }
    }

    @Test
    @DisplayName("record with stateful Sample instance")
    default void recordWithSample(MeterRegistry registry) {
        Timer timer = registry.timer("myTimer");
        Timer.Sample sample = Timer.start(registry);

        clock(registry).add(10, TimeUnit.NANOSECONDS);
        sample.stop(timer);
        clock(registry).add(step());

        assertAll(() -> assertEquals(1L, timer.count()),
                () -> assertEquals(10, timer.totalTime(TimeUnit.NANOSECONDS), 1.0e-12));
    }

    @Test
    default void recordMax(MeterRegistry registry) {
        Timer timer = registry.timer("my.timer");
        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(1, TimeUnit.SECONDS);

        clock(registry).add(step()); // for Atlas, which is step rather than ring-buffer based
        assertThat(timer.max(TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(1000);

        //noinspection ConstantConditions
        clock(registry).add(Duration.ofMillis(step().toMillis() * DistributionStatisticConfig.DEFAULT.getBufferLength()));
        assertThat(timer.max(TimeUnit.SECONDS)).isEqualTo(0);
    }

    @Test
    @DisplayName("callable task that throws exception is still recorded")
    default void recordCallableException(MeterRegistry registry) {
        Timer t = registry.timer("myTimer");

        assertThrows(Exception.class, () -> {
            t.recordCallable(() -> {
                clock(registry).add(10, TimeUnit.NANOSECONDS);
                throw new Exception("uh oh");
            });
        });

        clock(registry).add(step());

        assertAll(() -> assertEquals(1L, t.count()),
                () -> assertEquals(10, t.totalTime(TimeUnit.NANOSECONDS), 1.0e-12));
    }

    @Deprecated
    @Test
    default void percentiles(MeterRegistry registry) {
        Timer t = Timer.builder("my.timer")
                .publishPercentiles(1)
                .register(registry);

        t.record(1, TimeUnit.MILLISECONDS);
        assertThat(t.percentile(1, TimeUnit.MILLISECONDS)).isEqualTo(1, Offset.offset(0.3));
        assertThat(t.percentile(0.5, TimeUnit.MILLISECONDS)).isEqualTo(Double.NaN);
    }

    @Deprecated
    @Test
    default void histogramCounts(MeterRegistry registry) {
        Timer t = Timer.builder("my.timer")
                .sla(Duration.ofMillis(1))
                .register(registry);

        t.record(1, TimeUnit.MILLISECONDS);
        assertThat(t.histogramCountAtValue((long) millisToUnit(1, TimeUnit.NANOSECONDS))).isEqualTo(1);
        assertThat(t.histogramCountAtValue(1)).isEqualTo(Double.NaN);
    }
}
