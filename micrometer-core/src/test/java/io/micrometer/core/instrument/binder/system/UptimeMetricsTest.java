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
package io.micrometer.core.instrument.binder.system;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.lang.management.RuntimeMXBean;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Uptime metrics.
 *
 * @author Michael Weirauch
 */
class UptimeMetricsTest {

    @Test
    void uptimeMetricsRuntime() {
        MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
        new UptimeMetrics().bindTo(registry);

        registry.get("process.uptime").timeGauge();
        registry.get("process.start.time").timeGauge();
    }

    @Test
    void uptimeMetricsMock() {
        RuntimeMXBean runtimeMXBean = mock(RuntimeMXBean.class);
        when(runtimeMXBean.getUptime()).thenReturn(1337L);
        when(runtimeMXBean.getStartTime()).thenReturn(4711L);
        // tag::example[]
        MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
        new UptimeMetrics(runtimeMXBean, emptyList()).bindTo(registry);

        assertThat(registry.get("process.uptime").timeGauge().value()).isEqualTo(1.337);
        assertThat(registry.get("process.start.time").timeGauge().value()).isEqualTo(4.711);
        // end::example[]
    }

}
