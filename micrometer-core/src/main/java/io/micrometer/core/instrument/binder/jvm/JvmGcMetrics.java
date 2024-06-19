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

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static io.micrometer.core.instrument.binder.jvm.JvmMemory.*;
import static java.util.Collections.emptyList;

/**
 * Record metrics that report a number of statistics related to garbage collection
 * emanating from the MXBean and also adds information about GC causes.
 * <p>
 * This provides metrics for OpenJDK garbage collectors (serial, parallel, G1, Shenandoah,
 * ZGC), OpenJ9 garbage collectors (gencon, balanced, opthruput, optavgpause, metronome)
 * and for Azul Prime's (formerly Zing) C4 GC (formerly GPGC).
 * <p>
 * WARNING: Older versions of Azul Prime (Zing) did not report timing information about
 * pauses and cycles correctly (duration of GC pauses and duration of the concurrent part
 * of the GC which runs in parallel to application threads and is not stopping the
 * application). See the
 * <a href="https://docs.azul.com/prime/release-notes#prime_stream_22_12_0_0">release
 * notes</a> and <a href="https://bugs.openjdk.org/browse/JDK-8265136">JDK-8265136</a>. If
 * you want better accuracy, please make sure that you use a newer version and the new
 * metrics are enabled.
 *
 * @author Jon Schneider
 * @author Tommy Ludwig
 * @author Andrew Krasny
 * @see GarbageCollectorMXBean
 */
@NonNullApi
@NonNullFields
public class JvmGcMetrics implements MeterBinder, AutoCloseable {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(JvmGcMetrics.class);

    private final boolean managementExtensionsPresent = isManagementExtensionsPresent();

    private final boolean garbageCollectorNotificationsAvailable = isGarbageCollectorNotificationsAvailable();

    // VisibleForTesting
    final boolean isGenerationalGc = isGenerationalGcConfigured();

    private final Iterable<Tag> tags;

    @Nullable
    private String allocationPoolName;

    private final Set<String> longLivedPoolNames = new HashSet<>();

    private final List<Runnable> notificationListenerCleanUpRunnables = new CopyOnWriteArrayList<>();

    private Counter allocatedBytes;

    @Nullable
    private Counter promotedBytes;

    private AtomicLong allocationPoolSizeAfter;

    private AtomicLong liveDataSize;

    private AtomicLong maxDataSize;

    public JvmGcMetrics() {
        this(emptyList());
    }

    public JvmGcMetrics(Iterable<Tag> tags) {
        for (MemoryPoolMXBean mbean : ManagementFactory.getMemoryPoolMXBeans()) {
            String name = mbean.getName();
            if (isAllocationPool(name)) {
                allocationPoolName = name;
            }
            if (isLongLivedPool(name)) {
                longLivedPoolNames.add(name);
            }
        }
        this.tags = tags;
    }

    // VisibleForTesting
    GcMetricsNotificationListener gcNotificationListener;

    @Override
    public void bindTo(MeterRegistry registry) {
        if (!this.managementExtensionsPresent || !this.garbageCollectorNotificationsAvailable) {
            return;
        }

        gcNotificationListener = new GcMetricsNotificationListener(registry);

        double maxLongLivedPoolBytes = getLongLivedHeapPools()
            .mapToDouble(mem -> getUsageValue(mem, MemoryUsage::getMax))
            .sum();

        maxDataSize = new AtomicLong((long) maxLongLivedPoolBytes);
        Gauge.builder("jvm.gc.max.data.size", maxDataSize, AtomicLong::get)
            .tags(tags)
            .description("Max size of long-lived heap memory pool")
            .baseUnit(BaseUnits.BYTES)
            .register(registry);

        liveDataSize = new AtomicLong();

        Gauge.builder("jvm.gc.live.data.size", liveDataSize, AtomicLong::get)
            .tags(tags)
            .description("Size of long-lived heap memory pool after reclamation")
            .baseUnit(BaseUnits.BYTES)
            .register(registry);

        allocatedBytes = Counter.builder("jvm.gc.memory.allocated")
            .tags(tags)
            .baseUnit(BaseUnits.BYTES)
            .description(
                    "Incremented for an increase in the size of the (young) heap memory pool after one GC to before the next")
            .register(registry);

        promotedBytes = (isGenerationalGc) ? Counter.builder("jvm.gc.memory.promoted")
            .tags(tags)
            .baseUnit(BaseUnits.BYTES)
            .description(
                    "Count of positive increases in the size of the old generation memory pool before GC to after GC")
            .register(registry) : null;

        allocationPoolSizeAfter = new AtomicLong(0L);

        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (!(gcBean instanceof NotificationEmitter)) {
                continue;
            }
            NotificationEmitter notificationEmitter = (NotificationEmitter) gcBean;
            notificationEmitter.addNotificationListener(gcNotificationListener, notification -> notification.getType()
                .equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION), null);
            notificationListenerCleanUpRunnables.add(() -> {
                try {
                    notificationEmitter.removeNotificationListener(gcNotificationListener);
                }
                catch (ListenerNotFoundException ignore) {
                }
            });
        }
    }

    class GcMetricsNotificationListener implements NotificationListener {

        private final MeterRegistry registry;

        GcMetricsNotificationListener(MeterRegistry registry) {
            this.registry = registry;
        }

        @Override
        public void handleNotification(Notification notification, Object ref) {
            CompositeData cd = (CompositeData) notification.getUserData();
            GarbageCollectionNotificationInfo notificationInfo = GarbageCollectionNotificationInfo.from(cd);

            String gcName = notificationInfo.getGcName();
            String gcCause = notificationInfo.getGcCause();
            String gcAction = notificationInfo.getGcAction();
            GcInfo gcInfo = notificationInfo.getGcInfo();
            long duration = gcInfo.getDuration();

            Tags gcTags = Tags.of("gc", gcName, "action", gcAction, "cause", gcCause).and(tags);
            if (isConcurrentPhase(gcCause, gcName)) {
                Timer.builder("jvm.gc.concurrent.phase.time")
                    .tags(gcTags)
                    .description("Time spent in concurrent phase")
                    .register(registry)
                    .record(duration, TimeUnit.MILLISECONDS);
            }
            else {
                Timer.builder("jvm.gc.pause")
                    .tags(gcTags)
                    .description("Time spent in GC pause")
                    .register(registry)
                    .record(duration, TimeUnit.MILLISECONDS);
            }

            final Map<String, MemoryUsage> before = gcInfo.getMemoryUsageBeforeGc();
            final Map<String, MemoryUsage> after = gcInfo.getMemoryUsageAfterGc();

            countPoolSizeDelta(before, after);

            final long longLivedBefore = longLivedPoolNames.stream()
                .mapToLong(pool -> before.get(pool).getUsed())
                .sum();
            final long longLivedAfter = longLivedPoolNames.stream().mapToLong(pool -> after.get(pool).getUsed()).sum();
            if (isGenerationalGc) {
                final long delta = longLivedAfter - longLivedBefore;
                if (delta > 0L) {
                    promotedBytes.increment(delta);
                }
            }

            // Some GC implementations such as G1 can reduce the old gen size as part of a
            // minor GC. To track the live data size we record the value if we see a
            // reduction in the long-lived heap size or after a major/non-generational GC.
            // ZGC Warmup notifications should be ignored since the long-lived heap size
            // is zero there.
            if ((longLivedAfter < longLivedBefore || shouldUpdateDataSizeMetrics(gcName))
                    && !isZgcWarmingUp(gcCause, longLivedAfter)) {
                liveDataSize.set(longLivedAfter);
                maxDataSize.set(longLivedPoolNames.stream().mapToLong(pool -> after.get(pool).getMax()).sum());
            }
        }

        private void countPoolSizeDelta(Map<String, MemoryUsage> before, Map<String, MemoryUsage> after) {
            if (allocationPoolName == null) {
                return;
            }
            final long beforeBytes = before.get(allocationPoolName).getUsed();
            final long afterBytes = after.get(allocationPoolName).getUsed();
            final long delta = beforeBytes - allocationPoolSizeAfter.get();
            allocationPoolSizeAfter.set(afterBytes);
            if (delta > 0L) {
                allocatedBytes.increment(delta);
            }
        }

        /**
         * Sometimes ZGC emits "Warmup" notification, where longLivedAfter is 0, we should
         * ignore those notifications, see: gh-4497. This method should detect these
         * cases.
         * @param gcCause The action performed by the garbage collector
         * @param longLivedAfter Size of the long-lived pools after collection
         * @return Whether the ZGC warmup notification is emitted with zero long-lived
         * pools size
         */
        private boolean isZgcWarmingUp(String gcCause, long longLivedAfter) {
            return longLivedAfter == 0 && "Warmup".equals(gcCause);
        }

        private boolean shouldUpdateDataSizeMetrics(String gcName) {
            return nonGenerationalGcShouldUpdateDataSize(gcName) || isMajorGenerationalGc(gcName);
        }

        private boolean isMajorGenerationalGc(String gcName) {
            return GcGenerationAge.fromGcName(gcName) == GcGenerationAge.OLD;
        }

        private boolean nonGenerationalGcShouldUpdateDataSize(String gcName) {
            return !isGenerationalGc
                    // Skip Shenandoah and ZGC gc notifications with the name Pauses due
                    // to missing memory pool size info
                    && !gcName.endsWith("Pauses");
        }

    }

    private boolean isGenerationalGcConfigured() {
        // Azul Prime's (formerly Zing) C4 GC (formerly GPGC) is always generational
        // and having more than one non-'tenured' pools is also considered to be
        // generational.
        int nonTenuredPools = 0;
        for (MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans()) {
            if (JvmMemory.isHeap(bean)) {
                String name = bean.getName();
                if (!name.contains("tenured")) {
                    nonTenuredPools++;
                    if (nonTenuredPools == 2) {
                        return true;
                    }
                }
                if (name.contains("GPGC")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isManagementExtensionsPresent() {
        if (ManagementFactory.getMemoryPoolMXBeans().isEmpty()) {
            // Substrate VM, for example, doesn't provide or support these beans (yet)
            log.warn("GC notifications will not be available because MemoryPoolMXBeans are not provided by the JVM");
            return false;
        }

        try {
            Class.forName("com.sun.management.GarbageCollectionNotificationInfo", false,
                    MemoryPoolMXBean.class.getClassLoader());
            return true;
        }
        catch (Throwable e) {
            // We are operating in a JVM without access to this level of detail
            log.warn("GC notifications will not be available because "
                    + "com.sun.management.GarbageCollectionNotificationInfo is not present");
            return false;
        }
    }

    private static boolean isGarbageCollectorNotificationsAvailable() {
        List<String> gcsWithoutNotification = new ArrayList<>();
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (!(gcBean instanceof NotificationEmitter)) {
                continue;
            }
            NotificationEmitter notificationEmitter = (NotificationEmitter) gcBean;
            boolean notificationAvailable = Arrays.stream(notificationEmitter.getNotificationInfo())
                .anyMatch(mBeanNotificationInfo -> Arrays.asList(mBeanNotificationInfo.getNotifTypes())
                    .contains(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION));
            if (notificationAvailable) {
                return true;
            }
            gcsWithoutNotification.add(gcBean.getName());
        }
        log.warn("GC notifications will not be available because no GarbageCollectorMXBean of the JVM provides any."
                + " GCs=" + gcsWithoutNotification);

        return false;
    }

    @Override
    public void close() {
        notificationListenerCleanUpRunnables.forEach(Runnable::run);
    }

    /**
     * Generalization of which parts of the heap are considered "young" or "old" for
     * multiple GC implementations
     */
    @NonNullApi
    enum GcGenerationAge {

        OLD, YOUNG, UNKNOWN;

        private static final Map<String, GcGenerationAge> knownCollectors = new HashMap<String, GcGenerationAge>() {
            {
                put("ConcurrentMarkSweep", OLD);
                put("Copy", YOUNG);
                put("G1 Old Generation", OLD);
                put("G1 Young Generation", YOUNG);
                put("MarkSweepCompact", OLD);
                put("PS MarkSweep", OLD);
                put("PS Scavenge", YOUNG);
                put("ParNew", YOUNG);
                put("global", OLD);
                put("scavenge", YOUNG);
                put("partial gc", YOUNG);
                put("global garbage collect", OLD);
                put("Epsilon", OLD);
                // GPGC (Azul's C4, see:
                // https://docs.azul.com/prime/release-notes#prime_stream_22_12_0_0)
                put("GPGC New", YOUNG); // old naming
                put("GPGC Old", OLD); // old naming
                put("GPGC New Cycles", YOUNG); // new naming
                put("GPGC Old Cycles", OLD); // new naming
                put("GPGC New Pauses", YOUNG); // new naming
                put("GPGC Old Pauses", OLD); // new naming
                put("ZGC Major Cycles", OLD); // do not include 'ZGC Major Pauses'; see
                                              // gh-2872
            }
        };

        static GcGenerationAge fromGcName(String gcName) {
            return knownCollectors.getOrDefault(gcName, UNKNOWN);
        }

    }

}
