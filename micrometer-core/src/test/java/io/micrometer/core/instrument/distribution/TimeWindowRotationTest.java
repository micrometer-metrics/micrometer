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
package io.micrometer.core.instrument.distribution;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.config.InvalidConfigurationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeWindowRotationTest {

    static Collection<Class<? extends AbstractTimeWindowHistogram<?, ?>>> histogramTypes() {
        return Arrays.asList(TimeWindowPercentileHistogram.class, TimeWindowFixedBoundaryHistogram.class);
    }

    private static void expectValidationFailure(Class<? extends AbstractTimeWindowHistogram<?, ?>> histogramType,
            DistributionStatisticConfig badConfig) {
        assertThatThrownBy(() -> newHistogram(histogramType, new MockClock(),
                badConfig.merge(DistributionStatisticConfig.DEFAULT)))
            .hasRootCauseExactlyInstanceOf(InvalidConfigurationException.class)
            .satisfies(cause -> assertThat(cause.getCause())
                .hasMessageStartingWith("Invalid distribution configuration:"));
    }

    private static AbstractTimeWindowHistogram<?, ?> newHistogram(
            Class<? extends AbstractTimeWindowHistogram<?, ?>> histogramType, MockClock clock,
            DistributionStatisticConfig config) throws Exception {
        return histogramType.getDeclaredConstructor(Clock.class, DistributionStatisticConfig.class, Boolean.TYPE)
            .newInstance(clock, config, false);
    }

    @ParameterizedTest
    @MethodSource("histogramTypes")
    void rotationIntervalValidation(Class<? extends AbstractTimeWindowHistogram<?, ?>> histogramType) {
        expectValidationFailure(histogramType,
                DistributionStatisticConfig.builder().expiry(Duration.ofMillis(9)).bufferLength(10).build());
    }

    @ParameterizedTest
    @MethodSource("histogramTypes")
    void samplesRecordedAfterLongIdleGapAreNotDiscarded(
            Class<? extends AbstractTimeWindowHistogram<?, ?>> histogramType) throws Exception {
        MockClock clock = new MockClock();
        try (AbstractTimeWindowHistogram<?, ?> histogram = newHistogram(histogramType, clock,
                DistributionStatisticConfig.builder()
                    .serviceLevelObjectives(10.0)
                    .expiry(Duration.ofMinutes(1))
                    .bufferLength(3)
                    .build()
                    .merge(DistributionStatisticConfig.DEFAULT))) {

            // Record once, then stay idle far longer than the whole ring buffer spans. A single
            // rotation must clear the buffer and fast-forward; if it only lags one buffer-length
            // per call it re-wipes the buffer on every subsequent record and destroys the burst.
            histogram.recordDouble(1);
            clock.add(Duration.ofMinutes(10));

            // A burst that all falls within the same window after the gap.
            for (int i = 0; i < 5; i++) {
                histogram.recordDouble(1);
            }

            assertThat(histogram.takeSnapshot(0, 0, 0).histogramCounts()).containsExactly(new CountAtBucket(10.0, 5));
        }
    }

}
