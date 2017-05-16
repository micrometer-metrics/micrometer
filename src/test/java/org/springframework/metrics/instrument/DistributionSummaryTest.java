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
package org.springframework.metrics.instrument;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DistributionSummaryTest {

    @DisplayName("multiple recordings are maintained")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void record(MeterRegistry registry) {
        DistributionSummary ds = registry.distributionSummary("myDistributionSummary");

        ds.record(10);
        assertAll(() -> assertEquals(1L, ds.count()),
                () -> assertEquals(10L, ds.totalAmount()));


        ds.record(10);
        assertAll(() -> assertEquals(2L, ds.count()),
                () -> assertEquals(20L, ds.totalAmount()));
    }

    @DisplayName("negative quantities are ignored")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void recordNegative(MeterRegistry collector) {
        DistributionSummary ds = collector.distributionSummary("myDistributionSummary");

        ds.record(-10);
        assertAll(() -> assertEquals(0, ds.count()),
                () -> assertEquals(-0L, ds.totalAmount()));
    }

    @DisplayName("record zero")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void recordZero(MeterRegistry collector) {
        DistributionSummary ds = collector.distributionSummary("myDistributionSummary");

        ds.record(0);
        assertAll(() -> assertEquals(1L, ds.count()),
                () -> assertEquals(0L, ds.totalAmount()));
    }
}
