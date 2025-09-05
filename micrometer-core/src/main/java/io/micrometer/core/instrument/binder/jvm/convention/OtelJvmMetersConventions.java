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
        return new MicrometerJvmMemoryMeterConventions(extraTags) {
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

    public static class OtelJvmClassLoadingMeterConventions extends MicrometerJvmClassLoadingMeterConventions {

        public OtelJvmClassLoadingMeterConventions() {
            super();
        }

        public OtelJvmClassLoadingMeterConventions(Tags extraTags) {
            super(extraTags);
        }

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
    public JvmThreadMeterConventions jvmThreadMeterConventions(Tags extraTags) {
        return new OpenTelemetryJvmThreadMeterConventions(extraTags);
    }

    @Override
    public JvmCpuMeterConventions jvmCpuMeterConventions(Tags extraTags) {
        return new MicrometerJvmCpuMeterConventions(extraTags) {
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

    public static class OpenTelemetryJvmThreadMeterConventions extends MicrometerJvmThreadMeterConventions {

        private final MeterConvention<Thread.State> threadCountConvention;

        OpenTelemetryJvmThreadMeterConventions(Tags extraTags) {
            super(extraTags);
            threadCountConvention = new SimpleMeterConvention<>("jvm.thread.count", this::getThreadStateTags);
        }

        private Tags getThreadStateTags(Thread.State state) {
            return getCommonTags().and("jvm.thread.state", state.name().toLowerCase(Locale.ROOT));
        }

        @Override
        public MeterConvention<Thread.State> threadCountConvention() {
            return this.threadCountConvention;
        }

    }

}
