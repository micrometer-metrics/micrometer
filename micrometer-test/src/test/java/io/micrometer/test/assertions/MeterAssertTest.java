/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.test.assertions;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.tck.MeterRegistryAssert;
import org.junit.jupiter.api.Test;

class MeterAssertTest {

    SimpleMeterRegistry simpleMeterRegistry = new SimpleMeterRegistry();

    MeterRegistryAssert meterRegistryAssert = MeterRegistryAssert.assertThat(simpleMeterRegistry);

    @Test
    void shouldAssertOnMeasures() {
        DistributionSummary meter = DistributionSummary.builder("foo").register(simpleMeterRegistry);

        meter.record(10.0);
        meter.record(20.0);

        meterRegistryAssert.meter("foo")
            .hasMeasurement(Statistic.COUNT, 2.0)
            .hasMeasurement(Statistic.TOTAL, 30.0)
            .hasMeasurement(Statistic.MAX, 20.0);
    }

    @Test
    void shouldAssertOnType() {
        DistributionSummary.builder("foo").register(simpleMeterRegistry).record(100.0);

        meterRegistryAssert.meter("foo").hasType(DistributionSummary.class);
    }

}
