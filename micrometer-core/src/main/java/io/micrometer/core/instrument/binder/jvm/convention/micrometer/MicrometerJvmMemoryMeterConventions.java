/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.core.instrument.binder.jvm.convention.micrometer;

import io.micrometer.core.instrument.Tags;

import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;

/**
 * Historical convention used in Micrometer instrumentation for JVM memory metrics.
 *
 * @see io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
 * @since 1.16.0
 * @deprecated use individual convention types such as
 * {@link MicrometerJvmMemoryUsedMeterConvention},
 * {@link MicrometerJvmMemoryCommittedMeterConvention},
 * {@link MicrometerJvmMemoryMaxMeterConvention}, and
 * {@link MicrometerJvmMemoryUsedAfterLastGcMeterConvention} instead
 */
@Deprecated
@SuppressWarnings("deprecation")
public class MicrometerJvmMemoryMeterConventions
        implements io.micrometer.core.instrument.binder.jvm.convention.JvmMemoryMeterConventions {

    protected final Tags extraTags;

    public MicrometerJvmMemoryMeterConventions() {
        this(Tags.empty());
    }

    public MicrometerJvmMemoryMeterConventions(Tags extraTags) {
        this.extraTags = extraTags;
    }

    protected Tags getCommonTags(MemoryPoolMXBean memoryPoolBean) {
        return this.extraTags.and(Tags.of("id", memoryPoolBean.getName(), "area",
                MemoryType.HEAP.equals(memoryPoolBean.getType()) ? "heap" : "nonheap"));
    }

    @Override
    public io.micrometer.core.instrument.binder.MeterConvention<MemoryPoolMXBean> getMemoryUsedConvention() {
        return new io.micrometer.core.instrument.binder.SimpleMeterConvention<>("jvm.memory.used", this::getCommonTags);
    }

    @Override
    public io.micrometer.core.instrument.binder.MeterConvention<MemoryPoolMXBean> getMemoryCommittedConvention() {
        return new io.micrometer.core.instrument.binder.SimpleMeterConvention<>("jvm.memory.committed",
                this::getCommonTags);
    }

    @Override
    public io.micrometer.core.instrument.binder.MeterConvention<MemoryPoolMXBean> getMemoryMaxConvention() {
        return new io.micrometer.core.instrument.binder.SimpleMeterConvention<>("jvm.memory.max", this::getCommonTags);
    }

}
