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
import io.micrometer.core.instrument.binder.jvm.convention.JvmMemoryCommittedMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.JvmMemoryMaxMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.JvmMemoryMeterConventions;
import io.micrometer.core.instrument.binder.jvm.convention.JvmMemoryUsedMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.micrometer.MicrometerJvmMemoryCommittedMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.micrometer.MicrometerJvmMemoryMaxMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.micrometer.MicrometerJvmMemoryUsedMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmMemoryCommittedMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmMemoryMaxMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmMemoryUsedMeterConvention;
import org.jspecify.annotations.Nullable;

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

    private final Tags extraTags;

    private final JvmMemoryUsedMeterConvention memoryUsedConvention;

    private final JvmMemoryCommittedMeterConvention memoryCommittedConvention;

    private final JvmMemoryMaxMeterConvention memoryMaxConvention;

    public JvmMemoryMetrics() {
        this(Tags.empty(), new MicrometerJvmMemoryUsedMeterConvention(), new MicrometerJvmMemoryCommittedMeterConvention(),
                new MicrometerJvmMemoryMaxMeterConvention());
    }

    /**
     * Uses the default convention with the provided extra tags.
     * @param extraTags tags to add to each meter's tags produced by this binder
     */
    public JvmMemoryMetrics(Iterable<Tag> extraTags) {
        this(Tags.of(extraTags), new MicrometerJvmMemoryUsedMeterConvention(),
                new MicrometerJvmMemoryCommittedMeterConvention(), new MicrometerJvmMemoryMaxMeterConvention());
    }

    /**
     * Memory metrics with extra tags and a specific convention applied to meters. The
     * supplied extra tags are not combined with the convention. Provide a convention that
     * applies the extra tags if that is the desired outcome. The convention only applies
     * to some meters.
     * @param extraTags these will be added to meters not covered by the convention
     * @param conventions custom conventions for applicable metrics
     * @since 1.16.0
     * @deprecated use {@link #builder()} to provide individual conventions
     */
    @Deprecated
    public JvmMemoryMetrics(Iterable<? extends Tag> extraTags, JvmMemoryMeterConventions conventions) {
        this.extraTags = Tags.of(extraTags);
        MeterConvention<MemoryPoolMXBean> used = conventions.getMemoryUsedConvention();
        this.memoryUsedConvention = JvmMemoryUsedMeterConvention.of(used.getName(), used::getTags);
        MeterConvention<MemoryPoolMXBean> committed = conventions.getMemoryCommittedConvention();
        this.memoryCommittedConvention = JvmMemoryCommittedMeterConvention.of(committed.getName(), committed::getTags);
        MeterConvention<MemoryPoolMXBean> max = conventions.getMemoryMaxConvention();
        this.memoryMaxConvention = JvmMemoryMaxMeterConvention.of(max.getName(), max::getTags);
    }

    private JvmMemoryMetrics(Tags extraTags, JvmMemoryUsedMeterConvention memoryUsedConvention,
            JvmMemoryCommittedMeterConvention memoryCommittedConvention,
            JvmMemoryMaxMeterConvention memoryMaxConvention) {
        this.extraTags = extraTags;
        this.memoryUsedConvention = memoryUsedConvention;
        this.memoryCommittedConvention = memoryCommittedConvention;
        this.memoryMaxConvention = memoryMaxConvention;
    }

    /**
     * Create a new builder for {@link JvmMemoryMetrics}.
     * @return a new builder
     * @since 1.16.0
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (BufferPoolMXBean bufferPoolBean : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            Iterable<Tag> tagsWithId = Tags.concat(extraTags, "id", bufferPoolBean.getName());

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
                .builder(memoryUsedConvention.getName(), memoryPoolBean,
                        (mem) -> getUsageValue(mem, MemoryUsage::getUsed))
                .tags(memoryUsedConvention.getTags(memoryPoolBean))
                .tags(extraTags)
                .description("The amount of used memory")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);

            Gauge
                .builder(memoryCommittedConvention.getName(), memoryPoolBean,
                        (mem) -> getUsageValue(mem, MemoryUsage::getCommitted))
                .tags(memoryCommittedConvention.getTags(memoryPoolBean))
                .tags(extraTags)
                .description("The amount of memory in bytes that is committed for the Java virtual machine to use")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);

            Gauge
                .builder(memoryMaxConvention.getName(), memoryPoolBean,
                        (mem) -> getUsageValue(mem, MemoryUsage::getMax))
                .tags(memoryMaxConvention.getTags(memoryPoolBean))
                .tags(extraTags)
                .description("The maximum amount of memory in bytes that can be used for memory management")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);
        }
    }

    /**
     * Builder for {@link JvmMemoryMetrics}.
     *
     * @since 1.16.0
     */
    public static class Builder {

        private Tags extraTags = Tags.empty();

        private @Nullable JvmMemoryUsedMeterConvention memoryUsedConvention;

        private @Nullable JvmMemoryCommittedMeterConvention memoryCommittedConvention;

        private @Nullable JvmMemoryMaxMeterConvention memoryMaxConvention;

        Builder() {
        }

        /**
         * Extra tags to add to meters registered by this binder.
         * @param extraTags tags to add
         * @return this builder
         */
        public Builder extraTags(Iterable<? extends Tag> extraTags) {
            this.extraTags = Tags.of(extraTags);
            return this;
        }

        /**
         * Custom convention for the memory used meter.
         * @param convention the convention to use
         * @return this builder
         */
        public Builder memoryUsedConvention(JvmMemoryUsedMeterConvention convention) {
            this.memoryUsedConvention = convention;
            return this;
        }

        /**
         * Custom convention for the memory committed meter.
         * @param convention the convention to use
         * @return this builder
         */
        public Builder memoryCommittedConvention(JvmMemoryCommittedMeterConvention convention) {
            this.memoryCommittedConvention = convention;
            return this;
        }

        /**
         * Custom convention for the memory max meter.
         * @param convention the convention to use
         * @return this builder
         */
        public Builder memoryMaxConvention(JvmMemoryMaxMeterConvention convention) {
            this.memoryMaxConvention = convention;
            return this;
        }

        /**
         * Use OpenTelemetry semantic conventions for all meters. Individual conventions
         * can still be overridden by calling the specific convention methods after this
         * one.
         * @return this builder
         */
        public Builder openTelemetryConventions() {
            this.memoryUsedConvention = new OpenTelemetryJvmMemoryUsedMeterConvention();
            this.memoryCommittedConvention = new OpenTelemetryJvmMemoryCommittedMeterConvention();
            this.memoryMaxConvention = new OpenTelemetryJvmMemoryMaxMeterConvention();
            return this;
        }

        /**
         * Build a new {@link JvmMemoryMetrics} instance.
         * @return a new {@link JvmMemoryMetrics}
         */
        public JvmMemoryMetrics build() {
            return new JvmMemoryMetrics(extraTags,
                    memoryUsedConvention != null ? memoryUsedConvention : new MicrometerJvmMemoryUsedMeterConvention(),
                    memoryCommittedConvention != null ? memoryCommittedConvention
                            : new MicrometerJvmMemoryCommittedMeterConvention(),
                    memoryMaxConvention != null ? memoryMaxConvention : new MicrometerJvmMemoryMaxMeterConvention());
        }

    }

}
