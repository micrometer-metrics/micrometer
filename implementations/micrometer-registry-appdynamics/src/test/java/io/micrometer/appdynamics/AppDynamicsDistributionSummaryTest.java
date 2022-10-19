/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.appdynamics;

import io.micrometer.appdynamics.aggregation.MetricSnapshot;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AppDynamicsDistributionSummaryTest {

    private AppDynamicsDistributionSummary victim;

    @BeforeEach
    void initialize() {
        Meter.Id id = new Meter.Id("test.id", Tags.empty(), null, null, Meter.Type.DISTRIBUTION_SUMMARY);

        victim = new AppDynamicsDistributionSummary(id, new MockClock(), 1);
    }

    @Test
    void testNegativeValuesAreIgnored() {
        victim.record(-100);
        assertRecordedValues(victim, 0, 0, 0, 0);
    }

    @Test
    void testRecordedValues() {
        victim.record(-100);
        victim.record(100);
        victim.record(115);
        assertRecordedValues(victim, 2, 100, 115, 215);

        victim.record(120);
        assertRecordedValues(victim.snapshot(), 3, 100, 120, 335);

        victim.record(-100);
        assertRecordedValues(victim, 0, 0, 0, 0);
        assertRecordedValues(victim.snapshot(), 0, 0, 0, 0);
    }

    @Test
    void testScaledRecordedValues() {
        final double scale = 1.5;

        Meter.Id id = new Meter.Id("test.id", Tags.empty(), null, null, Meter.Type.DISTRIBUTION_SUMMARY);
        AppDynamicsDistributionSummary victim = new AppDynamicsDistributionSummary(id, new MockClock(), scale);

        victim.record(-100);
        victim.record(100);
        victim.record(120);
        assertRecordedValues(victim, 2, (long) (100 * scale), (long) (120 * scale), (long) (220 * scale));
    }

    private void assertRecordedValues(AppDynamicsDistributionSummary summary, long count, long min, long max,
            long total) {
        assertThat(summary.count()).isEqualTo(count);
        assertThat(summary.min()).isEqualTo(min);
        assertThat(summary.max()).isEqualTo(max);
        assertThat(summary.totalAmount()).isEqualTo(total);
    }

    private void assertRecordedValues(MetricSnapshot snapshot, long count, long min, long max, long total) {
        assertThat(snapshot.count()).isEqualTo(count);
        assertThat(snapshot.min()).isEqualTo(min);
        assertThat(snapshot.max()).isEqualTo(max);
        assertThat(snapshot.total()).isEqualTo(total);
    }

}
