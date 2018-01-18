/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.system;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ProcessorMetricsTest {
    @Test
    void cpuMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        new ProcessorMetrics().bindTo(registry);

        assertThat(registry.mustFind("system.cpu.count").gauge().value()).isGreaterThan(0);
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            assertThat(registry.find("system.load.average.1m").gauge())
                .describedAs("Not present on windows").isNull();
        } else {
            assertThat(registry.mustFind("system.load.average.1m").gauge().value()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void hotspotCpuMetrics() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        assumeTrue(osBean.getClass().getName().equals("sun.management.OperatingSystemImpl"));

        MeterRegistry registry = new SimpleMeterRegistry();
        new ProcessorMetrics().bindTo(registry);

        assertThat(registry.mustFind("system.cpu.usage").gauge().value()).isGreaterThanOrEqualTo(0);
        assertThat(registry.mustFind("process.cpu.usage").gauge().value()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void openJ9CpuMetrics() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        assumeTrue(osBean.getClass().getName().equals("com.ibm.lang.management.internal.UnixExtendedOperatingSystem"));

        MeterRegistry registry = new SimpleMeterRegistry();
        new ProcessorMetrics().bindTo(registry);

        /*
         * We can't assert on values because these methods are documented to return "-1"
         * on the first call and a positive value - if supported - on subsequent calls.
         * This holds true for "system.cpu.usage" but not for "process.cpu.usage". The latter
         * needs some milliseconds of sleep before it actually returns a positive value
         * on a supported system. Thread.sleep() is flaky, though.
         */
        registry.mustFind("system.cpu.usage").gauge();
        registry.mustFind("process.cpu.usage").gauge();
    }
}
