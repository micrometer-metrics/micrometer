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

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static io.micrometer.core.instrument.MockClock.clock;
import static org.junit.jupiter.api.Assertions.*;

interface DistributionSummaryTest {
    Duration step();

    @Test
    @DisplayName("multiple recordings are maintained")
    default void record(MeterRegistry registry) {
        DistributionSummary ds = registry.summary("myDistributionSummary");

        ds.record(10);
        clock(registry).add(step());

        assertAll(() -> assertEquals(1L, ds.count()),
                () -> assertEquals(10L, ds.totalAmount()));

        ds.record(10);
        ds.record(10);
        clock(registry).add(step());

        assertAll(() -> assertTrue(ds.count() >= 2L),
                () -> assertTrue(ds.totalAmount() >= 20L));
    }

    @Test
    @DisplayName("negative quantities are ignored")
    default void recordNegative(MeterRegistry registry) {
        DistributionSummary ds = registry.summary("myDistributionSummary");

        ds.record(-10);
        assertAll(() -> assertEquals(0, ds.count()),
                () -> assertEquals(0L, ds.totalAmount()));
    }

    @Test
    @DisplayName("record zero")
    default void recordZero(MeterRegistry registry) {
        DistributionSummary ds = registry.summary("myDistributionSummary");

        ds.record(0);
        clock(registry).add(step());
        
        assertAll(() -> assertEquals(1L, ds.count()),
                () -> assertEquals(0L, ds.totalAmount()));
    }
}
