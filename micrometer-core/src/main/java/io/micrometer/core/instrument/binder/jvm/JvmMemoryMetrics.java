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
import io.micrometer.core.instrument.binder.MeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.JvmMemoryMeterConventions;
import io.micrometer.core.instrument.binder.jvm.convention.micrometer.MicrometerJvmMemoryMeterConventions;

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

    private final JvmMemoryMeterConventions conventions;

    public JvmMemoryMetrics() {
        this(Tags.empty(), new MicrometerJvmMemoryMeterConventions());
    }

    /**
     * Uses the default convention with the provided extra tags.
     * @param extraTags tags to add to each meter's tags produced by this binder
     */
    public JvmMemoryMetrics(Iterable<Tag> extraTags) {
        this(extraTags, new MicrometerJvmMemoryMeterConventions(Tags.of(extraTags)));
    }

    /**
     * Memory metrics with extra tags and a specific convention applied to meters. The
     * supplied extra tags are not combined with the convention. Provide a convention that
     * applies the extra tags if that is the desired outcome. The convention only applies
     * to some meters.
     * @param extraTags these will be added to meters not covered by the convention
     * @param conventions custom conventions for applicable metrics
     * @since 1.16.0
     */
    public JvmMemoryMetrics(Iterable<? extends Tag> extraTags, JvmMemoryMeterConventions conventions) {
        this.tags = Tags.of(extraTags);
        this.conventions = conventions;
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
            MeterConvention<MemoryPoolMXBean> memoryUsedConvention = conventions.getMemoryUsedConvention();
            Gauge
                .builder(memoryUsedConvention.getName(), memoryPoolBean,
                        (mem) -> getUsageValue(mem, MemoryUsage::getUsed))
                .tags(memoryUsedConvention.getTags(memoryPoolBean))
                .description("The amount of used memory")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);

            MeterConvention<MemoryPoolMXBean> memoryCommittedConvention = conventions.getMemoryCommittedConvention();
            Gauge
                .builder(memoryCommittedConvention.getName(), memoryPoolBean,
                        (mem) -> getUsageValue(mem, MemoryUsage::getCommitted))
                .tags(memoryCommittedConvention.getTags(memoryPoolBean))
                .description("The amount of memory in bytes that is committed for the Java virtual machine to use")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);

            MeterConvention<MemoryPoolMXBean> memoryMaxConvention = conventions.getMemoryMaxConvention();
            Gauge
                .builder(memoryMaxConvention.getName(), memoryPoolBean,
                        (mem) -> getUsageValue(mem, MemoryUsage::getMax))
                .tags(memoryMaxConvention.getTags(memoryPoolBean))
                .description("The maximum amount of memory in bytes that can be used for memory management")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);
        }
    }

}
