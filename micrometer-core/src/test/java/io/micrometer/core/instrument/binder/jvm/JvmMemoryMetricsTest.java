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
package io.micrometer.core.instrument.binder.jvm;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.Collection;
import java.util.stream.StreamSupport;

import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Tests for {@code JvmMemoryMetrics}.
 *
 * @author Michael Weirauch
 */
public class JvmMemoryMetricsTest {

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
        Gauge memUsed = registry.mustFind("jvm.memory.used").tags("area", area).gauge();
        assertThat(memUsed.value()).isGreaterThanOrEqualTo(0);
        assertThat(memUsed.getId().getBaseUnit()).isEqualTo("bytes");

        Gauge memCommitted = registry.mustFind("jvm.memory.committed").tags("area", area).gauge();
        assertThat(memCommitted.value()).isNotNull();
        assertThat(memCommitted.getId().getBaseUnit()).isEqualTo("bytes");

        Gauge memMax = registry.mustFind("jvm.memory.max").tags("area", area).gauge();
        assertThat(memMax.value()).isNotNull();
        assertThat(memMax.getId().getBaseUnit()).isEqualTo("bytes");
    }

    private void assertJvmBufferMetrics(MeterRegistry registry, String bufferId) {
        assertThat(registry.mustFind("jvm.buffer.count").tags("id", bufferId)
            .gauge().value()).isGreaterThanOrEqualTo(0);

        Gauge memoryUsedDirect = registry.mustFind("jvm.buffer.memory.used").tags("id", bufferId).gauge();
        assertThat(memoryUsedDirect.value()).isNotNull();
        assertThat(memoryUsedDirect.getId().getBaseUnit()).isEqualTo("bytes");

        Gauge bufferTotal = registry.mustFind("jvm.buffer.total.capacity").tags("id", bufferId).gauge();
        assertThat(bufferTotal.value()).isGreaterThanOrEqualTo(0);
        assertThat(bufferTotal.getId().getBaseUnit()).isEqualTo("bytes");
    }

}
