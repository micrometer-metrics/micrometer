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

import com.sun.management.GarbageCollectionNotificationInfo;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterConvention;
import io.micrometer.core.instrument.binder.MeterConventionGroup;

import java.lang.management.*;
import java.util.Locale;

public interface JvmMetersConventions {

    JvmMetersConventions DEFAULT = new JvmMetersConventions() {
    };

    default JvmMemoryMeterConventionGroup jvmMemoryMeterConventions(Tags extraTags) {
        return new JvmMemoryMeterConventionGroup(extraTags) {
        };
    }

    default JvmClassLoadingMeterConventionGroup jvmClassLoadingMeterConventions() {
        return JvmClassLoadingMeterConventionGroup.DEFAULT;
    }

    default JvmThreadMeterConventionGroup jvmThreadMeterConventions(Tags commonTags) {
        return new DefaultJvmThreadMeterConventionGroup(commonTags);
    }

    default JvmCpuMeterConventionGroup jvmCpuMeterConventions(Tags commonTags) {
        return new DefaultJvmCpuMeterConventionGroup(commonTags);
    }

    default JvmGcMeterConventionGroup jvmGcMeterConventions() {
        return new JvmGcMeterConventionGroup() {
        };
    }

    abstract class JvmMemoryMeterConventionGroup implements MeterConventionGroup<MemoryPoolMXBean> {

        protected final Tags extraTags;

        public JvmMemoryMeterConventionGroup(Tags extraTags) {
            this.extraTags = extraTags;
        }

        @Override
        public Tags getCommonTags(MemoryPoolMXBean memoryPoolBean) {
            return this.extraTags.and(Tags.of("id", memoryPoolBean.getName(), "area",
                    MemoryType.HEAP.equals(memoryPoolBean.getType()) ? "heap" : "nonheap"));
        }

        public MeterConvention<MemoryPoolMXBean> getMemoryUsedConvention() {
            return new MeterConvention<MemoryPoolMXBean>() {

                @Override
                public String getName() {
                    return "jvm.memory.used";
                }

                @Override
                public Tags getTags(MemoryPoolMXBean memoryPoolBean) {
                    return getCommonTags(memoryPoolBean);
                }
            };
        }

        public MeterConvention<MemoryPoolMXBean> getMemoryCommittedConvention() {
            return new MeterConvention<MemoryPoolMXBean>() {

                @Override
                public String getName() {
                    return "jvm.memory.committed";
                }

                @Override
                public Tags getTags(MemoryPoolMXBean memoryPoolBean) {
                    return getCommonTags(memoryPoolBean);
                }
            };
        }

        public MeterConvention<MemoryPoolMXBean> getMemoryMaxConvention() {
            return new MeterConvention<MemoryPoolMXBean>() {

                @Override
                public String getName() {
                    return "jvm.memory.max";
                }

                @Override
                public Tags getTags(MemoryPoolMXBean memoryPoolBean) {
                    return getCommonTags(memoryPoolBean);
                }
            };
        }

    }

    interface JvmClassLoadingMeterConventionGroup extends MeterConventionGroup<Void> {

        JvmClassLoadingMeterConventionGroup DEFAULT = new JvmClassLoadingMeterConventionGroup() {
        };

        default MeterConvention<Void> loadedConvention() {
            return () -> "jvm.classes.loaded.count";
        }

        default MeterConvention<Void> unloadedConvention() {
            return () -> "jvm.classes.unloaded";
        }

        default MeterConvention<Void> currentClassCountConvention() {
            return () -> "jvm.classes.loaded";
        }

    }

    interface JvmThreadMeterConventionGroup extends MeterConventionGroup<ThreadMXBean> {

        MeterConvention<Thread.State> threadCountConvention();

    }

    interface JvmCpuMeterConventionGroup extends MeterConventionGroup<Void> {

        default MeterConvention<Void> cpuTimeConvention() {
            return () -> "process.cpu.time";
        }

        default MeterConvention<Void> cpuCountConvention() {
            return () -> "system.cpu.count";
        }

        default MeterConvention<Void> cpuRecentUtilizationConvention() {
            return () -> "process.cpu.usage";
        }

    }

    interface JvmGcMeterConventionGroup extends MeterConventionGroup<GarbageCollectionNotificationInfo> {

        default MeterConvention<GarbageCollectionNotificationInfo> gcTimeConvention() {
            return new MeterConvention<GarbageCollectionNotificationInfo>() {
                @Override
                public String getName() {
                    return "jvm.gc.duration";
                }

                @Override
                public Tags getTags(GarbageCollectionNotificationInfo gcNotification) {
                    return getCommonTags(gcNotification).and("jvm.gc.action", gcNotification.getGcAction(),
                            "jvm.gc.name", gcNotification.getGcName(), "jvm.gc.cause", gcNotification.getGcCause());
                }
            };
        }

    }

    class DefaultJvmCpuMeterConventionGroup implements JvmCpuMeterConventionGroup {

        private final Tags commonTags;

        public DefaultJvmCpuMeterConventionGroup(Tags commonTags) {
            this.commonTags = commonTags;
        }

        @Override
        public Tags getCommonTags(Void context) {
            return this.commonTags;
        }

    }

    class DefaultJvmThreadMeterConventionGroup implements JvmThreadMeterConventionGroup {

        final Tags commonTags;

        final MeterConvention<Thread.State> threadCountConvention;

        public DefaultJvmThreadMeterConventionGroup(Tags commonTags) {
            this.commonTags = commonTags;
            this.threadCountConvention = new DefaultThreadCountMeterConvention(this.commonTags);
        }

        @Override
        public Tags getCommonTags(ThreadMXBean context) {
            return this.commonTags;
        }

        @Override
        public MeterConvention<Thread.State> threadCountConvention() {
            return threadCountConvention;
        }

        static class DefaultThreadCountMeterConvention implements MeterConvention<Thread.State> {

            private final Tags commonTags;

            private DefaultThreadCountMeterConvention(Tags commonTags) {
                this.commonTags = commonTags;
            }

            @Override
            public String getName() {
                return "jvm.threads.states";
            }

            @Override
            public Tags getTags(Thread.State state) {
                return commonTags.and(Tags.of("state", getStateTagValue(state)));
            }

            private static String getStateTagValue(Thread.State state) {
                return state.name().toLowerCase(Locale.ROOT).replace('_', '-');
            }

        }

    }

}
