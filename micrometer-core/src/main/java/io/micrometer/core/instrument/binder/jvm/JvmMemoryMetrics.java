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
package io.micrometer.core.instrument.binder.jvm;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.convention.JvmMemoryMeterConventions;
import io.micrometer.core.instrument.binder.jvm.convention.MicrometerJvmMemoryMeterConventions;

import java.lang.management.*;

import static io.micrometer.core.instrument.binder.jvm.JvmMemory.getUsageValue;

/**
 * Record metrics that report utilization of various memory and buffer pools.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @see MemoryPoolMXBean
 * @see BufferPoolMXBean
 */
public class JvmMemoryMetrics implements MeterBinder {

    private final Tags tags;

    private final JvmMemoryMeterConventions convention;

    public JvmMemoryMetrics() {
        this(Tags.empty(), new MicrometerJvmMemoryMeterConventions());
    }

    /**
     * @param tags additional tags to add to each meter's tags produced by this binder
     */
    public JvmMemoryMetrics(Iterable<Tag> tags) {
        this(tags, new MicrometerJvmMemoryMeterConventions(Tags.of(tags)));
    }

    /**
     * @param convention
     * @since 1.16.0
     */
    public JvmMemoryMetrics(Iterable<? extends Tag> tags, JvmMemoryMeterConventions convention) {
        this.tags = Tags.of(tags);
        this.convention = convention;
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
            Gauge
                .builder(convention.getMemoryUsedConvention().getName(), memoryPoolBean,
                        (mem) -> getUsageValue(mem, MemoryUsage::getUsed))
                .tags(convention.getMemoryUsedConvention().getTags(memoryPoolBean))
                .description("The amount of used memory")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);

            Gauge
                .builder(convention.getMemoryCommittedConvention().getName(), memoryPoolBean,
                        (mem) -> getUsageValue(mem, MemoryUsage::getCommitted))
                .tags(convention.getMemoryCommittedConvention().getTags(memoryPoolBean))
                .description("The amount of memory in bytes that is committed for the Java virtual machine to use")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);

            Gauge
                .builder(convention.getMemoryMaxConvention().getName(), memoryPoolBean,
                        (mem) -> getUsageValue(mem, MemoryUsage::getMax))
                .tags(convention.getMemoryMaxConvention().getTags(memoryPoolBean))
                .description("The maximum amount of memory in bytes that can be used for memory management")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);
        }
    }

}
