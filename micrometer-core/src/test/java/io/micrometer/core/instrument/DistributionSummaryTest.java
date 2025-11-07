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
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.simple.CountingMode;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.testsupport.system.CapturedOutput;
import io.micrometer.core.testsupport.system.OutputCaptureExtension;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class DistributionSummaryTest {

    @Test
    void histogramsInCumulativeMode() {
        MockClock clock = new MockClock();
        MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
        DistributionSummary summary = DistributionSummary.builder("my.summary")
            .serviceLevelObjectives(1.0)
            .register(registry);

        summary.record(1);

        // Histogram bucket counts DO roll over at the step interval, so decay.
        assertThat(summary.takeSnapshot().histogramCounts()).containsExactly(new CountAtBucket(1.0, 1));
        clock.add(SimpleConfig.DEFAULT.step());
        assertThat(summary.takeSnapshot().histogramCounts()).containsExactly(new CountAtBucket(1.0, 0));
    }

    @Test
    void histogramsInStepMode() {
        MockClock clock = new MockClock();
        MeterRegistry registry = new SimpleMeterRegistry(new SimpleConfig() {
            @Override
            public @Nullable String get(String key) {
                return null;
            }

            @Override
            public CountingMode mode() {
                return CountingMode.STEP;
            }
        }, clock);

        DistributionSummary summary = DistributionSummary.builder("my.summary")
            .serviceLevelObjectives(1.0)
            .register(registry);

        summary.record(1);

        assertThat(summary.takeSnapshot().histogramCounts()).containsExactly(new CountAtBucket(1.0, 1));
        clock.add(SimpleConfig.DEFAULT.step());
        assertThat(summary.takeSnapshot().histogramCounts()).containsExactly(new CountAtBucket(1.0, 0));
    }

    @Test
    void takeSnapshotShouldNotThrowExceptionEvenIfDoubleHistogramThrowsException(CapturedOutput output) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DistributionSummary summary = DistributionSummary.builder("my.summary")
            .publishPercentiles(0.1, 0.25, 0.5, 0.75, 0.9, 0.95, 0.99)
            .register(registry);
        summary.record(1);
        summary.record(1E-10);
        summary.takeSnapshot();
        summary.record(1E-20);
        summary.takeSnapshot();
        assertThat(output).contains("Failed to accumulate.");
    }

}
