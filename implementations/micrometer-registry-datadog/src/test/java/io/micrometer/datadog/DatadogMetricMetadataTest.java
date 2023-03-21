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
package io.micrometer.datadog;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatadogMetricMetadataTest {

    @Test
    void escapesStringsInDescription() {
        DatadogMetricMetadata metricMetadata = new DatadogMetricMetadata(Counter.builder("name")
            .tag("key", "value")
            .description("The /\"recent cpu usage\" for the Java Virtual Machine process")
            .register(new SimpleMeterRegistry())
            .getId(), Statistic.COUNT, true, null);

        assertThat(metricMetadata.editMetadataBody()).isEqualTo(
                "{\"type\":\"count\",\"description\":\"The /\\\"recent cpu usage\\\" for the Java Virtual Machine process\"}");
    }

    @Test
    void unitsAreConverted() {
        DatadogMetricMetadata metricMetadata = new DatadogMetricMetadata(Timer.builder("name")
            .tag("key", "value")
            .description("Time spent in GC pause")
            .register(new DatadogMeterRegistry(new DatadogConfig() {
                @Override
                public String apiKey() {
                    return "fake";
                }

                @Override
                public String get(String key) {
                    return null;
                }
            }, Clock.SYSTEM))
            .getId(), Statistic.TOTAL_TIME, false, null);

        assertThat(metricMetadata.editMetadataBody()).isNull();
    }

}
