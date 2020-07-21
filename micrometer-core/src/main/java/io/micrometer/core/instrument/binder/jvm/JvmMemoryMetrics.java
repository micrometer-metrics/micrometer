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
package io.micrometer.core.instrument.binder.jvm;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;

import java.lang.management.*;

import static io.micrometer.core.instrument.binder.jvm.JvmMemory.getUsageValue;
import static io.micrometer.core.instrument.binder.jvm.JvmMemory.getTotalAreaUsageValue;
import static io.micrometer.core.instrument.binder.jvm.JvmMemory.getHeapUsagePercent;
import static java.util.Collections.emptyList;

/**
 * Record metrics that report utilization of various memory and buffer pools.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @see MemoryPoolMXBean
 * @see BufferPoolMXBean
 */
@NonNullApi
@NonNullFields
public class JvmMemoryMetrics implements MeterBinder {
    private final Iterable<Tag> tags;

    public JvmMemoryMetrics() {
        this(emptyList());
    }

    public JvmMemoryMetrics(Iterable<Tag> tags) {
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (BufferPoolMXBean bufferPoolBean : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            Iterable<Tag> tagsWithId = Tags.concat(tags, "id", bufferPoolBean.getName());

            Gauge.builder("jvm.buffer.count", bufferPoolBean, BufferPoolMXBean::getCount)
                    .tags(tagsWithId)
                    .description("An estimate of the number of buffers in the pool")
                    .baseUnit(BaseUnits.BUFFERS)
                    .register(registry);

            Gauge.builder("jvm.buffer.memory.used", bufferPoolBean, BufferPoolMXBean::getMemoryUsed)
                .tags(tagsWithId)
                .description("An estimate of the memory that the Java virtual machine is using for this buffer pool")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);

            Gauge.builder("jvm.buffer.total.capacity", bufferPoolBean, BufferPoolMXBean::getTotalCapacity)
                .tags(tagsWithId)
                .description("An estimate of the total capacity of the buffers in this pool")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);
        }

        for (MemoryPoolMXBean memoryPoolBean : ManagementFactory.getPlatformMXBeans(MemoryPoolMXBean.class)) {
            String area = MemoryType.HEAP.equals(memoryPoolBean.getType()) ? "heap" : "nonheap";
            Iterable<Tag> tagsWithId = Tags.concat(tags, "id", memoryPoolBean.getName(), "area", area);

            Gauge.builder("jvm.memory.used", memoryPoolBean, (mem) -> getUsageValue(mem, MemoryUsage::getUsed))
                .tags(tagsWithId)
                .description("The amount of used memory")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);

            Gauge.builder("jvm.memory.committed", memoryPoolBean, (mem) -> getUsageValue(mem, MemoryUsage::getCommitted))
                .tags(tagsWithId)
                .description("The amount of memory in bytes that is committed for the Java virtual machine to use")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);

            Gauge.builder("jvm.memory.max", memoryPoolBean, (mem) -> getUsageValue(mem, MemoryUsage::getMax))
                .tags(tagsWithId)
                .description("The maximum amount of memory in bytes that can be used for memory management")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);
        }

        //The used and committed size of the returned memory usage is the sum of those values of all heap/non-heap memory pools
        // whereas the max size of the returned memory usage represents the setting of the non-heap/non-heap memory which may
        // not be the sum of those of all non-heap memory pools. If the setting is missing for non-heap memory pools these values come as -1.

        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

        Iterable<Tag> tagsWithId = Tags.concat(tags,"id", "total", "area", "heap");

        Gauge.builder("jvm.memory.used", memoryMXBean, (mem) -> getTotalAreaUsageValue(mem, MemoryUsage::getUsed, "heap"))
                .tags(tagsWithId)
                .description("The amount of used memory")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);

        Gauge.builder("jvm.memory.max", memoryMXBean, (mem) -> getTotalAreaUsageValue(mem, MemoryUsage::getMax, "heap"))
                .tags(tagsWithId)
                .description("The maximum amount of memory in bytes that can be used for memory management")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);

        Gauge.builder("jvm.memory.committed", memoryMXBean, (mem) -> getTotalAreaUsageValue(mem, MemoryUsage::getCommitted, "heap"))
                .tags(tagsWithId)
                .description("The amount of memory in bytes that is committed for the Java virtual machine to use")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);

        Gauge.builder("jvm.memory.heap_used_percent", memoryMXBean, (mem) -> getHeapUsagePercent(mem))
                .tags(tags)
                .description("The percentage of used memory with respect to maximum amount of memory that can be used for memory management of heap")
                .register(registry);

        tagsWithId = Tags.concat(tags,"id", "total", "area", "nonheap");

        Gauge.builder("jvm.memory.used", memoryMXBean, (mem) -> getTotalAreaUsageValue(mem, MemoryUsage::getUsed, "nonheap"))
                .tags(tagsWithId)
                .description("The amount of used memory")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);

        Gauge.builder("jvm.memory.max", memoryMXBean, (mem) -> getTotalAreaUsageValue(mem, MemoryUsage::getMax, "nonheap"))
                .tags(tagsWithId)
                .description("The maximum amount of memory in bytes that can be used for memory management")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);

        Gauge.builder("jvm.memory.committed", memoryMXBean, (mem) -> getTotalAreaUsageValue(mem, MemoryUsage::getCommitted, "nonheap"))
                .tags(tagsWithId)
                .description("The amount of memory in bytes that is committed for the Java virtual machine to use")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);

    }

}
