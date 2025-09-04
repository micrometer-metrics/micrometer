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

import javax.management.Notification;
import javax.management.openmbean.CompositeData;
import java.util.Locale;

/**
 * Defines methods to get the various conventions related to JVM metrics.
 *
 * @since 1.16.0
 * @see #DEFAULT the default implementation
 */
public interface JvmMetersConventions {

    /**
     * Implementation with the default conventions.
     */
    JvmMetersConventions DEFAULT = new JvmMetersConventions() {
    };

    default JvmMemoryMeterConventions jvmMemoryMeterConventions(Tags extraTags) {
        return new JvmMemoryMeterConventions(extraTags) {
        };
    }

    default JvmClassLoadingMeterConventions jvmClassLoadingMeterConventions() {
        return JvmClassLoadingMeterConventions.DEFAULT;
    }

    default JvmThreadMeterConventionGroup jvmThreadMeterConventions(Tags extraTags) {
        return new DefaultJvmThreadMeterConventionGroup(extraTags);
    }

    default JvmCpuMeterConventions jvmCpuMeterConventions(Tags extraTags) {
        return new JvmCpuMeterConventions(extraTags) {
        };
    }

    default JvmGcMeterConventionGroup jvmGcMeterConventions() {
        return new JvmGcMeterConventionGroup() {
        };
    }

    interface JvmThreadMeterConventionGroup {

        MeterConvention<Thread.State> threadCountConvention();

    }

    interface JvmGcMeterConventionGroup {

        default MeterConvention<Notification> gcTimeConvention() {
            return new MeterConvention<Notification>() {
                @Override
                public String getName() {
                    return "jvm.gc.duration";
                }

                @Override
                public Tags getTags(Notification notification) {
                    CompositeData cd = (CompositeData) notification.getUserData();
                    GarbageCollectionNotificationInfo gcNotification = GarbageCollectionNotificationInfo.from(cd);
                    return Tags.of("jvm.gc.action", gcNotification.getGcAction(), "jvm.gc.name",
                            gcNotification.getGcName(), "jvm.gc.cause", gcNotification.getGcCause());
                }
            };
        }

    }

    class DefaultJvmThreadMeterConventionGroup implements JvmThreadMeterConventionGroup {

        final Tags extraTags;

        final MeterConvention<Thread.State> threadCountConvention;

        public DefaultJvmThreadMeterConventionGroup(Tags extraTags) {
            this.extraTags = extraTags;
            this.threadCountConvention = new DefaultThreadCountMeterConvention(this.getCommonTags());
        }

        protected Tags getCommonTags() {
            return this.extraTags;
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
