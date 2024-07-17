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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for {@link MemoryMetrics}.
 *
 * @author Fábio C. Martins
 */
class MemoryMetricsTest {

    MeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void setup() {
        new MemoryMetrics().bindTo(registry);
    }

    @Test
    void memoryMetrics() {
        Gauge virtualMemCommited = registry.get("system.virtualmemory.commited").gauge();
        assertThat(virtualMemCommited.getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);

        Gauge swapTotal = registry.get("system.swap.total").gauge();
        assertThat(swapTotal.value()).isNotNegative();
        assertThat(swapTotal.getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);

        Gauge swapFree = registry.get("system.swap.free").gauge();
        assertThat(swapFree.value()).isNotNegative();
        assertThat(swapFree.getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);

        Gauge memFree = registry.get("system.memory.free").gauge();
        assertThat(memFree.getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);

        Gauge memTotal = registry.get("system.memory.total").gauge();
        assertThat(memTotal.value()).isNotNegative();
        assertThat(memTotal.getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);
    }

    @Test
    void hotspotMetrics() {
        assumeFalse(isOpenJ9());
        assertThat(registry.get("system.virtualmemory.commited").gauge().value()).isNotNegative();
        assertThat(registry.get("system.memory.free").gauge().value()).isNotNegative();
    }

    @Test
    void j9Metrics() {
        assumeTrue(isOpenJ9());

        assertThat(registry.get("system.virtualmemory.commited").gauge().value()).isGreaterThanOrEqualTo(-1);
        assertThat(registry.get("system.memory.free").gauge().value()).isGreaterThanOrEqualTo(-1);
    }

    private boolean isOpenJ9() {
        return classExists("com.ibm.lang.management.OperatingSystemMXBean");
    }

    private boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        }
        catch (ClassNotFoundException e) {
            return false;
        }
    }

}
