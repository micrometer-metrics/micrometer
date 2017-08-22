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
            Iterable<Tag> tags = Tags.zip("id", bufferPoolBean.getName());

            registry.gauge("jvm.buffer.count", tags, bufferPoolBean, BufferPoolMXBean::getCount);

            registry.gaugeBuilder("jvm.buffer.memory.used", bufferPoolBean, BufferPoolMXBean::getMemoryUsed)
                .baseUnit("bytes")
                .tags(tags)
                .create();

            registry.gaugeBuilder("jvm.buffer.total.capacity", bufferPoolBean, BufferPoolMXBean::getTotalCapacity)
                .baseUnit("bytes")
                .tags(tags)
                .create();
        }

        for (MemoryPoolMXBean memoryPoolBean : ManagementFactory.getPlatformMXBeans(MemoryPoolMXBean.class)) {
            Iterable<Tag> tags = Tags.zip("id", memoryPoolBean.getName());

            registry.gaugeBuilder("jvm.memory.used", memoryPoolBean, (mem) -> mem.getUsage().getUsed())
                .baseUnit("bytes")
                .tags(tags)
                .create();

            registry.gaugeBuilder("jvm.memory.committed", memoryPoolBean, (mem) -> mem.getUsage().getCommitted())
                .baseUnit("bytes")
                .tags(tags)
                .create();

            registry.gaugeBuilder("jvm.memory.max", memoryPoolBean, (mem) -> mem.getUsage().getMax())
                .baseUnit("bytes")
                .tags(tags)
                .create();
        }
    }
}
