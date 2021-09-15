/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for {@link ProcessorMetrics}.
 *
 * @author Jon Schneider
 * @author Michael Weirauch
 * @author Clint Checketts
 * @author Tommy Ludwig
 * @author Johnny Lim
 */
class ProcessorMetricsTest {

    MeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void setup() {
        new ProcessorMetrics().bindTo(registry);
    }

    @Test
    void cpuMetrics() {
        assertThat(registry.get("system.cpu.count").gauge().value()).isGreaterThan(0);
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            assertThat(registry.find("system.load.average.1m").gauge())
                .describedAs("Not present on windows").isNull();
        } else {
            assertThat(registry.get("system.load.average.1m").gauge().value()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void hotspotCpuMetrics() {
        assumeTrue(!isOpenJ9());

        assertThat(registry.get("system.cpu.usage").gauge().value()).isGreaterThanOrEqualTo(0);
        assertThat(registry.get("process.cpu.usage").gauge().value()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void openJ9CpuMetrics() {
        assumeTrue(isOpenJ9());

        /*
         * These methods are documented to return "-1" on the first call
         * and a positive value - if supported - on subsequent calls.
         * This holds true for "system.cpu.usage" but not for "process.cpu.usage". The latter
         * needs some milliseconds of sleep before it actually returns a positive value
         * on a supported system. Thread.sleep() is flaky, though.
         */
        assertThat(registry.get("system.cpu.usage").gauge().value()).isGreaterThanOrEqualTo(-1);
        assertThat(registry.get("process.cpu.usage").gauge().value()).isGreaterThanOrEqualTo(-1);
    }

    private boolean isOpenJ9() {
        return classExists("com.ibm.lang.management.OperatingSystemMXBean");
    }

    private boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
