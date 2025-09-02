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

import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.ThreadMXBean;
import java.util.Locale;

public class OtelJvmMetersConventions implements JvmMetersConventions {

    @Override
    public JvmMemoryMeterConventionGroup jvmMemoryMeterConventions(Tags extraTags) {
        return new JvmMemoryMeterConventionGroup(extraTags) {
            @Override
            public Tags getCommonTags(MemoryPoolMXBean memoryPoolBean) {
                return this.extraTags.and(Tags.of("jvm.memory.pool.name", memoryPoolBean.getName(), "jvm.memory.type",
                        MemoryType.HEAP.equals(memoryPoolBean.getType()) ? "heap" : "non_heap"));
            }

            @Override
            public MeterConvention<MemoryPoolMXBean> getMemoryMaxConvention() {
                return new MeterConvention<MemoryPoolMXBean>() {
                    @Override
                    public String getName() {
                        return "jvm.memory.limit";
                    }

                    @Override
                    public Tags getTags(MemoryPoolMXBean context) {
                        return getCommonTags(context);
                    }
                };
            }
        };
    }

    @Override
    public JvmClassLoadingMeterConventionGroup jvmClassLoadingMeterConventions() {
        return new JvmClassLoadingMeterConventionGroup() {
            @Override
            public MeterConvention<Void> loadedConvention() {
                return () -> "jvm.class.loaded";
            }

            @Override
            public MeterConvention<Void> unloadedConvention() {
                return () -> "jvm.class.unloaded";
            }

            @Override
            public MeterConvention<Void> currentClassCountConvention() {
                return () -> "jvm.class.count";
            }
        };
    }

    @Override
    public JvmThreadMeterConventionGroup jvmThreadMeterConventions(Tags commonTags) {
        return new OpenTelemetryJvmThreadMeterConventionGroup(commonTags);
    }

    @Override
    public JvmCpuMeterConventionGroup jvmCpuMeterConventions(Tags commonTags) {
        return new JvmCpuMeterConventionGroup() {
            @Override
            public MeterConvention<Void> cpuTimeConvention() {
                return () -> "jvm.cpu.time";
            }

            @Override
            public MeterConvention<Void> cpuCountConvention() {
                return () -> "jvm.cpu.count";
            }

            @Override
            public MeterConvention<Void> cpuRecentUtilizationConvention() {
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

        @Override
        public Tags getCommonTags(ThreadMXBean context) {
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
