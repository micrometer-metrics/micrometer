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
package io.micrometer.core.instrument.binder;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.management.RuntimeMXBean;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Uptime metrics.
 *
 * @author Michael Weirauch
 */
class UptimeMetricsTest {

    @Test
    void uptimeMetricsRuntime() {
        MeterRegistry registry = new SimpleMeterRegistry();
        new UptimeMetrics().bindTo(registry);

        assertThat(registry.find("uptime").meter()).isPresent();
    }

    @Test
    void uptimeMetricsMock() {
        MeterRegistry registry = new SimpleMeterRegistry();
        RuntimeMXBean runtimeMXBean = mock(RuntimeMXBean.class);
        when(runtimeMXBean.getUptime()).thenReturn(1337L);
        new UptimeMetrics(runtimeMXBean).bindTo(registry);

        assertThat(registry.find("uptime").value(Statistic.Value, 1337.0).meter()).isPresent();
    }

}
