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

        final Tag directBufferTag = Tag.of("id", "direct");
        final Tag mappedBufferTag = Tag.of("id", "mapped");
        assertMeters(registry.find("jvm.buffer.count").meters(), false, null, directBufferTag,
                mappedBufferTag);
        assertMeters(registry.find("jvm.buffer.memory.used").meters(), true, "bytes",
                directBufferTag, mappedBufferTag);
        assertMeters(registry.find("jvm.buffer.total.capacity").meters(), false, "bytes",
                directBufferTag, mappedBufferTag);

        final Tag heapTag = Tag.of("area", "heap");
        final Tag nonHeapTag = Tag.of("area", "nonheap");
        assertMeters(registry.find("jvm.memory.used").meters(), false, "bytes", heapTag,
                nonHeapTag);
        assertMeters(registry.find("jvm.memory.committed").meters(), false, "bytes", heapTag,
                nonHeapTag);
        assertMeters(registry.find("jvm.memory.max").meters(), true, "bytes", heapTag, nonHeapTag);
    }

    private static void assertMeters(Collection<Meter> meters, boolean valueCanBeNegative,
            String baseUnit, Tag firstTag, Tag secondTag) {
        // assumes tags to be distributed evenly among meters
        assertThat(meters).asList().extracting(o -> {
            return (Gauge) o;
        }).allSatisfy(g -> {
            if (valueCanBeNegative) {
                assertThat(g.value()).isNotNull();
            } else {
                assertThat(g.value()).isGreaterThanOrEqualTo(0);
            }
            if (baseUnit != null) {
                assertThat(g.getId().getBaseUnit()).isEqualTo("bytes");
            }
        }).areExactly(meters.size() / 2, new Condition<Meter>(g -> {
            return StreamSupport.stream(g.getId().getTags().spliterator(), false)
                    .filter(t -> t.equals(firstTag)).count() > 0;
        }, "carrying " + firstTag)).areExactly(meters.size() / 2, new Condition<Meter>(g -> {
            return StreamSupport.stream(g.getId().getTags().spliterator(), false)
                    .filter(t -> t.equals(secondTag)).count() > 0;
        }, "carrying " + secondTag));
    }

}
