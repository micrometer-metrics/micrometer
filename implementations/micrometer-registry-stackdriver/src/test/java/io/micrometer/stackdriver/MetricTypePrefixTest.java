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
package io.micrometer.stackdriver;

import com.google.monitoring.v3.TimeSeries;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MockClock;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MetricTypePrefixTest {

    private final Map<String, String> config = new HashMap<>(
            Collections.singletonMap("stackdriver.projectId", "projectId"));

    private final StackdriverMeterRegistry meterRegistry = new StackdriverMeterRegistry(config::get, new MockClock());

    @Test
    void metricTypePrefixWhenNotConfigured() {
        StackdriverMeterRegistry.Batch batch = meterRegistry.new Batch();
        List<TimeSeries> timeSeries = meterRegistry
            .createCounter(batch, Counter.builder("counter").register(meterRegistry))
            .collect(Collectors.toList());
        assertThat(timeSeries).hasSize(1);
        assertThat(timeSeries.get(0).getMetric().getType()).isEqualTo("custom.googleapis.com/counter");
    }

    @Test
    void metricTypePrefixWhenConfigured() {
        config.put("stackdriver.metricTypePrefix", "external.googleapis.com/user/");

        StackdriverMeterRegistry.Batch batch = meterRegistry.new Batch();
        List<TimeSeries> timeSeries = meterRegistry
            .createCounter(batch, Counter.builder("counter").register(meterRegistry))
            .collect(Collectors.toList());
        assertThat(timeSeries).hasSize(1);
        assertThat(timeSeries.get(0).getMetric().getType()).isEqualTo("external.googleapis.com/user/counter");
    }

}
