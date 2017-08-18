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

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;

import static java.util.Collections.singletonList;

/**
 * Record metrics that report utilization of various memory and buffer pools.
 *
 * @see MemoryPoolMXBean
 * @see BufferPoolMXBean
 */
public class JvmMemoryMetrics implements MeterBinder {

    @Override
    public void bindTo(MeterRegistry registry) {
        for (BufferPoolMXBean bufferPoolBean : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            Iterable<Tag> tags = singletonList(Tag.of("id", bufferPoolBean.getName()));

            registry.gauge("jvm.buffer.count", tags, bufferPoolBean, BufferPoolMXBean::getCount);
            registry.gauge("jvm.buffer.memory.used", tags, bufferPoolBean, BufferPoolMXBean::getMemoryUsed);
            registry.gauge("jvm.buffer.total.capacity", tags, bufferPoolBean, BufferPoolMXBean::getTotalCapacity);
        }

        for (MemoryPoolMXBean memoryPoolBean : ManagementFactory.getPlatformMXBeans(MemoryPoolMXBean.class)) {
            Iterable<Tag> tags = singletonList(Tag.of("id", memoryPoolBean.getName()));

            registry.gauge("jvm.memory.used", tags, memoryPoolBean, (mem) -> mem.getUsage().getUsed());
            registry.gauge("jvm.memory.committed", tags, memoryPoolBean, (mem) -> mem.getUsage().getCommitted());
            registry.gauge("jvm.memory.max", tags, memoryPoolBean, (mem) -> mem.getUsage().getMax());
        }
    }
}
