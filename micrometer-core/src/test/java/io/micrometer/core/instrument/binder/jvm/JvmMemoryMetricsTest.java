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
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmMemoryMeterConventions;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@code JvmMemoryMetrics}.
 *
 * @author Michael Weirauch
 */
class JvmMemoryMetricsTest {

    MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void memoryMetrics() {
        new JvmMemoryMetrics().bindTo(registry);

        assertJvmBufferMetrics("direct");
        assertJvmBufferMetrics("mapped");

        assertJvmMemoryMetrics("heap");
        assertJvmMemoryMetrics("nonheap");
    }

    @Test
    void memoryMetricsWithExtraTags() {
        Tags extraTags = Tags.of("extra", "tag");
        new JvmMemoryMetrics(extraTags).bindTo(registry);

        assertJvmBufferMetrics("direct", extraTags);
        assertJvmBufferMetrics("mapped", extraTags);

        assertJvmMemoryMetrics("heap", extraTags);
        assertJvmMemoryMetrics("nonheap", extraTags);
    }

    @Test
    void otelMemoryMetrics() {
        Tags extraTags = Tags.empty();
        new JvmMemoryMetrics(extraTags, new OpenTelemetryJvmMemoryMeterConventions(extraTags)).bindTo(registry);

        assertJvmBufferMetrics("direct");
        assertJvmBufferMetrics("mapped");

        assertJvmMemoryMetrics("heap", extraTags, true);
        assertJvmMemoryMetrics("non_heap", extraTags, true);
    }

    @Test
    void otelMemoryMetricsWithExtraTags() {
        Tags extraTags = Tags.of("extra", "tag");
        new JvmMemoryMetrics(extraTags, new OpenTelemetryJvmMemoryMeterConventions(extraTags)).bindTo(registry);

        assertJvmBufferMetrics("direct");
        assertJvmBufferMetrics("mapped");

        assertJvmMemoryMetrics("heap", extraTags, true);
        assertJvmMemoryMetrics("non_heap", extraTags, true);
    }

    private void assertJvmMemoryMetrics(String area) {
        assertJvmMemoryMetrics(area, Tags.empty());
    }

    private void assertJvmMemoryMetrics(String area, Tags extraTags) {
        assertJvmMemoryMetrics(area, extraTags, false);
    }

    private void assertJvmMemoryMetrics(String area, Tags extraTags, boolean isOtel) {
        String maxMemoryMeterName = isOtel ? "jvm.memory.limit" : "jvm.memory.max";
        Tags tags = Tags.of(isOtel ? "jvm.memory.type" : "area", area).and(extraTags);

        Gauge memUsed = registry.get("jvm.memory.used").tags(tags).gauge();
        assertThat(memUsed.value()).isGreaterThanOrEqualTo(0);
        assertThat(memUsed.getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);

        Gauge memCommitted = registry.get("jvm.memory.committed").tags(tags).gauge();
        assertThat(memCommitted.value()).isNotNaN();
        assertThat(memCommitted.getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);

        Gauge memMax = registry.get(maxMemoryMeterName).tags(tags).gauge();
        assertThat(memMax.value()).isNotNaN();
        assertThat(memMax.getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);
    }

    private void assertJvmBufferMetrics(String bufferId) {
        assertJvmBufferMetrics(bufferId, Tags.empty());
    }

    private void assertJvmBufferMetrics(String bufferId, Tags extraTags) {
        assertThat(registry.get("jvm.buffer.count").tags("id", bufferId).gauge().value()).isGreaterThanOrEqualTo(0);

        Gauge memoryUsedDirect = registry.get("jvm.buffer.memory.used").tags("id", bufferId).tags(extraTags).gauge();
        assertThat(memoryUsedDirect.value()).isNotNaN();
        assertThat(memoryUsedDirect.getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);

        Gauge bufferTotal = registry.get("jvm.buffer.total.capacity").tags("id", bufferId).tags(extraTags).gauge();
        assertThat(bufferTotal.value()).isGreaterThanOrEqualTo(0);
        assertThat(bufferTotal.getId().getBaseUnit()).isEqualTo(BaseUnits.BYTES);
    }

}
