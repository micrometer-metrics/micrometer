/*
 * Copyright 2020 VMware, Inc.
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
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics.GcMetricsNotificationListener;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

/**
 * Tests for {@link JvmGcMetrics}.
 *
 * @author Johnny Lim
 */
@GcTest
class JvmGcMetricsTest {

    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    JvmGcMetrics binder;

    @BeforeEach
    void beforeEach() {
        this.binder = new JvmGcMetrics();
        binder.bindTo(registry);
    }

    @Test
    void noJvmImplementationSpecificApiSignatures() {
        JavaClasses importedClasses = new ClassFileImporter()
            .importPackages("io.micrometer.core.instrument.binder.jvm");

        ArchRule noSunManagementInMethodSignatures = methods().should()
            .notHaveRawReturnType(resideInAPackage("com.sun.management.."))
            .andShould()
            .notHaveRawParameterTypes(DescribedPredicate.anyElementThat(resideInAPackage("com.sun.management..")));

        noSunManagementInMethodSignatures.check(importedClasses);
    }

    @Test
    void gcMetricsAvailableAfterGc() {
        System.gc();
        await().timeout(200, TimeUnit.MILLISECONDS)
            .alias("NotificationListener takes time after GC")
            .untilAsserted(() -> assertThat(registry.find("jvm.gc.live.data.size").gauge().value()).isPositive());
        assertThat(registry.find("jvm.gc.memory.allocated").counter().count()).isPositive();
        assertThat(registry.find("jvm.gc.max.data.size").gauge().value()).isPositive();

        if (!binder.isGenerationalGc) {
            return;
        }
        // cannot guarantee an object was promoted, so cannot check for positive count
        assertThat(registry.find("jvm.gc.memory.promoted").counter()).isNotNull();
    }

    @Test
    @Disabled("Garbage collection can happen before JvmGcMetrics are registered, making our metrics not match overall counts/timings")
    // available for some platforms and distributions earlier, but broadest availability
    // in an LTS is 17
    @EnabledForJreRange(min = JRE.JAVA_17)
    void gcTimingIsCorrect() {
        System.gc();
        long pausePhaseCount = 0;
        long pauseTimeMs = 0;
        long concurrentPhaseCount = 0;
        long concurrentTimeMs = 0;
        for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (mbean.getName().contains("Pauses")) {
                pausePhaseCount += mbean.getCollectionCount();
                pauseTimeMs += mbean.getCollectionTime();
            }
            else if (mbean.getName().contains("Cycles")) {
                concurrentPhaseCount += mbean.getCollectionCount();
                concurrentTimeMs += mbean.getCollectionTime();
            }
            System.out
                .println(mbean.getName() + " (" + mbean.getCollectionCount() + ") " + mbean.getCollectionTime() + "ms");
        }
        checkPhaseCount(pausePhaseCount, concurrentPhaseCount);
        checkCollectionTime(pauseTimeMs, concurrentTimeMs);
    }

    @Test
    @Issue("gh-2872")
    void sizeMetricsNotSetToZero() throws InterruptedException {
        GcMetricsNotificationListener gcMetricsNotificationListener = binder.gcNotificationListener;
        NotificationCapturingListener capturingListener = new NotificationCapturingListener();
        Collection<Runnable> notificationListenerCleanUpRunnables = new ArrayList<>();

        // register capturing notification listener
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (!(gcBean instanceof NotificationEmitter)) {
                continue;
            }
            NotificationEmitter notificationEmitter = (NotificationEmitter) gcBean;
            notificationEmitter.addNotificationListener(capturingListener, notification -> notification.getType()
                .equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION), null);
            notificationListenerCleanUpRunnables.add(() -> {
                try {
                    notificationEmitter.removeNotificationListener(capturingListener);
                }
                catch (ListenerNotFoundException ignore) {
                }
            });
        }

        try {
            // capture real gc notifications
            System.gc();
            // reduce flakiness by sleeping to give time for gc notifications to be sent
            // to listeners.
            // note: we cannot just wait for any notification because we don't know how
            // many notifications to expect.
            // this can still be flawed if we didn't wait for all notifications and
            // proceed with assertions on an
            // incomplete set of notifications.
            Thread.sleep(100);
            List<Notification> notifications = capturingListener.getNotifications();
            assertThat(notifications).isNotEmpty();
            // replay each notification and check size metrics not set to zero
            for (Notification notification : notifications) {
                gcMetricsNotificationListener.handleNotification(notification, null);
                assertThat(registry.get("jvm.gc.live.data.size").gauge().value()).isPositive();
                assertThat(registry.get("jvm.gc.max.data.size").gauge().value()).isPositive();
            }
        }
        finally {
            notificationListenerCleanUpRunnables.forEach(Runnable::run);
        }
    }

    static class NotificationCapturingListener implements NotificationListener {

        private final List<Notification> notifications = new ArrayList<>();

        List<Notification> getNotifications() {
            return Collections.unmodifiableList(notifications);
        }

        @Override
        public void handleNotification(Notification notification, Object handback) {
            notifications.add(notification);
        }

    }

    private void checkPhaseCount(long expectedPauseCount, long expectedConcurrentCount) {
        await().atMost(200, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            long observedPauseCount = registry.find("jvm.gc.pause").timers().stream().mapToLong(Timer::count).sum();
            long observedConcurrentCount = registry.find("jvm.gc.concurrent.phase.time")
                .timers()
                .stream()
                .mapToLong(Timer::count)
                .sum();
            assertThat(observedPauseCount).isEqualTo(expectedPauseCount);
            assertThat(observedConcurrentCount).isEqualTo(expectedConcurrentCount);
        });
    }

    private void checkCollectionTime(long expectedPauseTimeMs, long expectedConcurrentTimeMs) {
        await().atMost(200, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            double observedPauseTimeMs = registry.find("jvm.gc.pause")
                .timers()
                .stream()
                .mapToDouble(timer -> timer.totalTime(TimeUnit.MILLISECONDS))
                .sum();
            double observedConcurrentTimeMs = registry.find("jvm.gc.concurrent.phase.time")
                .timers()
                .stream()
                .mapToDouble(timer -> timer.totalTime(TimeUnit.MILLISECONDS))
                .sum();
            // small difference can happen when less than 1ms timing gets rounded
            assertThat(observedPauseTimeMs).isCloseTo(expectedPauseTimeMs, within(1d));
            assertThat(observedConcurrentTimeMs).isCloseTo(expectedConcurrentTimeMs, within(1d));
        });
    }

}
