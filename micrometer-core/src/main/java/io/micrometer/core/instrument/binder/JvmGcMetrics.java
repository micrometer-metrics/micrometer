/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Record metrics that report a number of statistics related to garbage
 * collection emanating from the MXBean and also adds information about GC causes.
 *
 * @see GarbageCollectorMXBean
 */
public class JvmGcMetrics implements MeterBinder {
    private String youngGenPoolName;
    private String oldGenPoolName;

    public JvmGcMetrics() {
        for (MemoryPoolMXBean mbean : ManagementFactory.getMemoryPoolMXBeans()) {
            if (isYoungGenPool(mbean.getName()))
                youngGenPoolName = mbean.getName();
            if (isOldGenPool(mbean.getName()))
                oldGenPoolName = mbean.getName();
        }
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        // Max size of old generation memory pool
        AtomicLong maxDataSize = registry.gauge("jvm_gc_max_data_size", new AtomicLong(0L));

        // Size of old generation memory pool after a full GC
        AtomicLong liveDataSize = registry.gauge("jvm_gc_live_data_size", new AtomicLong(0L));

        // Incremented for any positive increases in the size of the old generation memory pool
        // before GC to after GC
        Counter promotionRate = registry.meter("jvm_gc_promotion_rate").counter();

        // Incremented for the increase in the size of the young generation memory pool after one GC
        // to before the next
        Counter allocationRate = registry.meter("jvm_gc_allocation_rate").counter();

        // start watching for GC notifications
        final AtomicLong youngGenSizeAfter = new AtomicLong(0L);

        for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (mbean instanceof NotificationEmitter) {
                ((NotificationEmitter) mbean).addNotificationListener((notification, ref) -> {
                    final String type = notification.getType();
                    if (type.equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
                        CompositeData cd = (CompositeData) notification.getUserData();
                        GarbageCollectionNotificationInfo notificationInfo = GarbageCollectionNotificationInfo.from(cd);

                        registry.timer((isConcurrentPhase(notificationInfo) ? "jvm_gc_concurrent_phase_time" : "jvm_gc_pause"),
                                "action", notificationInfo.getGcAction(), "cause", notificationInfo.getGcCause()
                        ).record(notificationInfo.getGcInfo().getDuration(), TimeUnit.MILLISECONDS);

                        GcInfo gcInfo = notificationInfo.getGcInfo();

                        // Update promotion and allocation counters
                        final Map<String, MemoryUsage> before = gcInfo.getMemoryUsageBeforeGc();
                        final Map<String, MemoryUsage> after = gcInfo.getMemoryUsageAfterGc();

                        if (oldGenPoolName != null) {
                            final long oldBefore = before.get(oldGenPoolName).getUsed();
                            final long oldAfter = after.get(oldGenPoolName).getUsed();
                            final long delta = oldAfter - oldBefore;
                            if (delta > 0L) {
                                promotionRate.increment(delta);
                            }

                            // Some GC implementations such as G1 can reduce the old gen size as part of a minor GC. To track the
                            // live data size we record the value if we see a reduction in the old gen heap size or
                            // after a major GC.
                            if (oldAfter < oldBefore || GcGenerationAge.fromName(notificationInfo.getGcName()) == GcGenerationAge.OLD) {
                                liveDataSize.set(oldAfter);
                                final long oldMaxAfter = after.get(oldGenPoolName).getMax();
                                maxDataSize.set(oldMaxAfter);
                            }
                        }

                        if (youngGenPoolName != null) {
                            final long youngBefore = before.get(youngGenPoolName).getUsed();
                            final long youngAfter = after.get(youngGenPoolName).getUsed();
                            final long delta = youngBefore - youngGenSizeAfter.get();
                            youngGenSizeAfter.set(youngAfter);
                            if (delta > 0L) {
                                allocationRate.increment(delta);
                            }
                        }
                    }
                }, null, null);
            }
        }
    }

    private boolean isConcurrentPhase(GarbageCollectionNotificationInfo info) {
        return "No GC".equals(info.getGcCause());
    }

    private boolean isOldGenPool(String name) {
        return name.endsWith("Old Gen") || name.endsWith("Tenured Gen");
    }

    private boolean isYoungGenPool(String name) {
        return name.endsWith("Eden Space");
    }
}

/**
 * Generalization of which parts of the heap are considered "young" or "old" for multiple GC implementations
 */
enum GcGenerationAge {
    OLD,
    YOUNG,
    UNKNOWN;

    private static Map<String, GcGenerationAge> knownCollectors = new HashMap<String, GcGenerationAge>() {{
        put("ConcurrentMarkSweep", OLD);
        put("Copy", YOUNG);
        put("G1 Old Generation", OLD);
        put("G1 Young Generation", YOUNG);
        put("MarkSweepCompact", OLD);
        put("PS MarkSweep", OLD);
        put("PS Scavenge", YOUNG);
        put("ParNew", YOUNG);
    }};

    static GcGenerationAge fromName(String name) {
        GcGenerationAge t = knownCollectors.get(name);
        return (t == null) ? UNKNOWN : t;
    }
}
