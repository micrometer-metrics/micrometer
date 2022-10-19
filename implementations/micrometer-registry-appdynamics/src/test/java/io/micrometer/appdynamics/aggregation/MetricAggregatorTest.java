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
package io.micrometer.appdynamics.aggregation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MetricAggregatorTest {

    private final MetricAggregator victim = new MetricAggregator();

    @Test
    void testNegativeValuesAreIgnored() {
        victim.recordNonNegative(-100);
        assertRecordedValues(victim, 0, 0, 0, 0);
    }

    @Test
    void testRecordedValues() {
        victim.recordNonNegative(-100);
        victim.recordNonNegative(100);
        victim.recordNonNegative(50);
        assertRecordedValues(victim, 2, 50, 100, 150);

        victim.recordNonNegative(120);
        victim.recordNonNegative(-100);
        assertRecordedValues(victim, 3, 50, 120, 270);
    }

    @Test
    void testResetRecordedValues() {
        victim.recordNonNegative(-100);
        victim.recordNonNegative(100);
        victim.recordNonNegative(50);
        assertRecordedValues(victim, 2, 50, 100, 150);

        victim.reset();
        assertRecordedValues(victim, 0, 0, 0, 0);
    }

    private void assertRecordedValues(MetricAggregator aggregator, long count, long min, long max, long total) {
        assertThat(aggregator.count()).isEqualTo(count);
        assertThat(aggregator.min()).isEqualTo(min);
        assertThat(aggregator.max()).isEqualTo(max);
        assertThat(aggregator.total()).isEqualTo(total);
    }

}
