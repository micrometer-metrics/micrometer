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

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for {@link MemoryMetrics}.
 *
 * @author Fábio C. Martins
 */
class MemoryMetricsTest {
    private final InternalLogger logger = InternalLoggerFactory.getInstance(MemoryMetricsTest.class);

    MeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void setup() {
        new MemoryMetrics().bindTo(registry);
    }

    @Test
    void memoryMetrics() {
        Gauge virtualMemCommited = registry.get("system.virtualmemory.commited").gauge();
        assertThat(virtualMemCommited.value()).isGreaterThanOrEqualTo(0);
        assertThat(virtualMemCommited.getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);
        logger.info("{}", virtualMemCommited.value());

        Gauge swapTotal = registry.get("system.swap.total").gauge();
        assertThat(swapTotal.value()).isGreaterThanOrEqualTo(0);
        assertThat(swapTotal.getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);
        logger.info("{}", swapTotal.value());

        Gauge swapFree = registry.get("system.swap.free").gauge();
        assertThat(swapFree.value()).isGreaterThanOrEqualTo(0);
        assertThat(swapFree.getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);
        logger.info("{}", swapFree.value());

        Gauge memFree = registry.get("system.memory.free").gauge();
        assertThat(memFree.value()).isGreaterThanOrEqualTo(0);
        assertThat(memFree.getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);
        logger.info("{}", memFree.value());

        Gauge memTotal = registry.get("system.memory.total").gauge();
        assertThat(memTotal.value()).isGreaterThanOrEqualTo(0);
        assertThat(memTotal.getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);
        logger.info("{}", memTotal.value());

        await().atMost(Duration.ofMillis(5000));
        logger.info("{}", virtualMemCommited.value());
        logger.info("{}", swapTotal.value());
        logger.info("{}", swapFree.value());
        logger.info("{}", memFree.value());
        logger.info("{}", memTotal.value());
    }

    @Test
    void hotspotMetrics() {
        assumeFalse(isOpenJ9());

        assertThat(registry.get("system.virtualmemory.commited").gauge().value()).isNotNegative();
        assertThat(registry.get("system.swap.total").gauge().value()).isNotNegative();
        assertThat(registry.get("system.swap.free").gauge().value()).isNotNegative();
        assertThat(registry.get("system.memory.free").gauge().value()).isNotNegative();
        assertThat(registry.get("system.memory.total").gauge().value()).isNotNegative();
    }

    @Test
    void j9Metrics() {
        assumeTrue(isOpenJ9());

        assertThat(registry.get("system.virtualmemory.commited").gauge().value()).isGreaterThanOrEqualTo(-1);
        assertThat(registry.get("system.swap.total").gauge().value()).isNotNegative();
        assertThat(registry.get("system.swap.free").gauge().value()).isNotNegative();
        assertThat(registry.get("system.memory.free").gauge().value()).isGreaterThanOrEqualTo(-1);
        assertThat(registry.get("system.memory.total").gauge().value()).isNotNegative();
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
