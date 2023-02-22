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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
            public String get(String key) {
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

}
