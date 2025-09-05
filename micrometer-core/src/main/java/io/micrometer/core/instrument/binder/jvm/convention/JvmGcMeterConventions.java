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

// intentionally not public for now
interface JvmGcMeterConventions {

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
                return Tags.of("jvm.gc.action", gcNotification.getGcAction(), "jvm.gc.name", gcNotification.getGcName(),
                        "jvm.gc.cause", gcNotification.getGcCause());
            }
        };
    }

}
