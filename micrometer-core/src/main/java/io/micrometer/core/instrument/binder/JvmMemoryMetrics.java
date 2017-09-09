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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;

import static java.util.Collections.emptyList;

/**
 * Record metrics that report utilization of various memory and buffer pools.
 *
 * @see MemoryPoolMXBean
 * @see BufferPoolMXBean
 *
 * @author Jon Schneider
 */
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

            registry.gauge(registry.createId("jvm.buffer.count", tagsWithId,
                "An estimate of the number of buffers in the pool"),
                bufferPoolBean, BufferPoolMXBean::getCount);

            registry.gauge(registry.createId("jvm.buffer.memory.used", tagsWithId,
                "An estimate of the memory that the Java virtual machine is using for this buffer pool",
                "bytes"),
                bufferPoolBean, BufferPoolMXBean::getMemoryUsed);

            registry.gauge(registry.createId("jvm.buffer.total.capacity", tagsWithId,
                "An estimate of the total capacity of the buffers in this pool",
                "bytes"),
                bufferPoolBean, BufferPoolMXBean::getTotalCapacity);
        }

        for (MemoryPoolMXBean memoryPoolBean : ManagementFactory.getPlatformMXBeans(MemoryPoolMXBean.class)) {
            String area = MemoryType.HEAP.equals(memoryPoolBean.getType()) ? "heap" : "nonheap";
            Iterable<Tag> tagsWithId = Tags.concat(tags,"id", memoryPoolBean.getName(), "area", area);

            registry.gauge(registry.createId("jvm.memory.used", tagsWithId,
                "The amount of used memory", "bytes"), memoryPoolBean, (mem) -> mem.getUsage().getUsed());

            registry.gauge(registry.createId("jvm.memory.committed", tagsWithId,
                "The amount of memory in bytes that is committed for  the Java virtual machine to use", "bytes"),
                memoryPoolBean, (mem) -> mem.getUsage().getCommitted());

            registry.gauge(registry.createId("jvm.memory.max", tagsWithId,
                "The maximum amount of memory in bytes that can be used for memory management", "bytes"),
                memoryPoolBean, (mem) -> mem.getUsage().getMax());
        }
    }
}
