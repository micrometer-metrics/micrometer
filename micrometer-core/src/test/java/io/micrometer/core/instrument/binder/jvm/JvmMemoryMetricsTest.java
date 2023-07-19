/*
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.core.instrument.binder.jvm;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Tests for {@code JvmMemoryMetrics}.
 *
 * @author Michael Weirauch
 */
class JvmMemoryMetricsTest {

    @Test
    void memoryMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        new JvmMemoryMetrics().bindTo(registry);

        assertJvmBufferMetrics(registry, "direct");
        assertJvmBufferMetrics(registry, "mapped");

        assertJvmMemoryMetrics(registry, "heap");
        assertJvmMemoryMetrics(registry, "nonheap");
    }

    private void assertJvmMemoryMetrics(MeterRegistry registry, String area) {
        Gauge memUsed = registry.get("jvm.memory.used").tags("area", area).gauge();
        assertThat(memUsed.value()).isGreaterThanOrEqualTo(0);
        assertThat(memUsed.getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);

        Gauge memCommitted = registry.get("jvm.memory.committed").tags("area", area).gauge();
        assertThat(memCommitted.value()).isNotNull();
        assertThat(memCommitted.getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);

        Gauge memMax = registry.get("jvm.memory.max").tags("area", area).gauge();
        assertThat(memMax.value()).isNotNull();
        assertThat(memMax.getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);
    }

    private void assertJvmBufferMetrics(MeterRegistry registry, String bufferId) {
        assertThat(registry.get("jvm.buffer.count").tags("id", bufferId).gauge().value()).isGreaterThanOrEqualTo(0);

        Gauge memoryUsedDirect = registry.get("jvm.buffer.memory.used").tags("id", bufferId).gauge();
        assertThat(memoryUsedDirect.value()).isNotNull();
        assertThat(memoryUsedDirect.getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);

        Gauge bufferTotal = registry.get("jvm.buffer.total.capacity").tags("id", bufferId).gauge();
        assertThat(bufferTotal.value()).isGreaterThanOrEqualTo(0);
        assertThat(bufferTotal.getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);
    }

}
