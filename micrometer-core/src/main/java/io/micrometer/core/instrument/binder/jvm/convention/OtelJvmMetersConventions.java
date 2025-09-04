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
package io.micrometer.core.instrument.binder.jvm.convention;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterConvention;
import io.micrometer.core.instrument.binder.SimpleMeterConvention;

import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.Locale;

public class OtelJvmMetersConventions extends MicrometerJvmMetersConventions {

    @Override
    public JvmMemoryMeterConventions jvmMemoryMeterConventions(Tags extraTags) {
        return new JvmMemoryMeterConventions(extraTags) {
            @Override
            protected Tags getCommonTags(MemoryPoolMXBean memoryPoolBean) {
                return this.extraTags.and(Tags.of("jvm.memory.pool.name", memoryPoolBean.getName(), "jvm.memory.type",
                        MemoryType.HEAP.equals(memoryPoolBean.getType()) ? "heap" : "non_heap"));
            }

            @Override
            public MeterConvention<MemoryPoolMXBean> getMemoryMaxConvention() {
                return new SimpleMeterConvention<>("jvm.memory.limit", this::getCommonTags);
            }
        };
    }

    @Override
    public JvmClassLoadingMeterConventions jvmClassLoadingMeterConventions() {
        return new OtelJvmClassLoadingMeterConventions();
    }

    public static class OtelJvmClassLoadingMeterConventions implements JvmClassLoadingMeterConventions {

        @Override
        public MeterConvention<Object> loadedConvention() {
            return new SimpleMeterConvention<>("jvm.class.loaded", getCommonTags());
        }

        @Override
        public MeterConvention<Object> unloadedConvention() {
            return new SimpleMeterConvention<>("jvm.class.unloaded", getCommonTags());
        }

        @Override
        public MeterConvention<Object> currentClassCountConvention() {
            return new SimpleMeterConvention<>("jvm.class.count", getCommonTags());
        }

    }

    @Override
    public JvmThreadMeterConventionGroup jvmThreadMeterConventions(Tags extraTags) {
        return new OpenTelemetryJvmThreadMeterConventionGroup(extraTags);
    }

    @Override
    public JvmCpuMeterConventions jvmCpuMeterConventions(Tags extraTags) {
        return new JvmCpuMeterConventions(extraTags) {
            @Override
            public MeterConvention<Object> cpuTimeConvention() {
                return () -> "jvm.cpu.time";
            }

            @Override
            public MeterConvention<Object> cpuCountConvention() {
                return () -> "jvm.cpu.count";
            }

            @Override
            public MeterConvention<Object> processCpuLoadConvention() {
                return () -> "jvm.cpu.recent_utilization";
            }
        };
    }

    public static class OpenTelemetryJvmThreadMeterConventionGroup implements JvmThreadMeterConventionGroup {

        private final Tags commonTags;

        private final MeterConvention<Thread.State> threadCountConvention;

        OpenTelemetryJvmThreadMeterConventionGroup(Tags commonTags) {
            this.commonTags = commonTags;
            threadCountConvention = new OtelJvmThreadCountConvention(this.commonTags);
        }

        public Tags getCommonTags() {
            return this.commonTags;
        }

        @Override
        public MeterConvention<Thread.State> threadCountConvention() {
            return this.threadCountConvention;
        }

        private static class OtelJvmThreadCountConvention implements MeterConvention<Thread.State> {

            private final Tags commonTags;

            public OtelJvmThreadCountConvention(Tags commonTags) {
                this.commonTags = commonTags;
            }

            @Override
            public String getName() {
                return "jvm.thread.count";
            }

            @Override
            public Tags getTags(Thread.State state) {
                return commonTags.and("jvm.thread.state", state.name().toLowerCase(Locale.ROOT));
            }

        }

    }

}
