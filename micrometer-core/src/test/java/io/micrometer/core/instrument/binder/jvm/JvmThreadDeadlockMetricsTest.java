/*
 * Copyright 2024 VMware, Inc.
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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link JvmThreadDeadlockMetrics}.
 *
 * @author Ruth Kurniawati
 */
class JvmThreadDeadlockMetricsTest {

    MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void deadlockedThreadMetrics() {
        new JvmThreadDeadlockMetrics().bindTo(registry);
        final CountDownLatch lock1IsLocked = new CountDownLatch(1);
        final CountDownLatch lock2IsLocked = new CountDownLatch(1);
        final Lock lock1 = new ReentrantLock();
        final Lock lock2 = new ReentrantLock();

        final DeadlockedThread deadlockedThread1 = new DeadlockedThread(lock1IsLocked, lock1, lock2IsLocked, lock2);
        final DeadlockedThread deadlockedThread2 = new DeadlockedThread(lock2IsLocked, lock2, lock1IsLocked, lock1);
        deadlockedThread1.start();
        deadlockedThread2.start();

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(registry.get("jvm.threads.deadlocked").gauge().value()).isEqualTo(2);
            assertThat(registry.get("jvm.threads.deadlocked.monitor").gauge().value()).isEqualTo(0);
        });
        deadlockedThread1.interrupt();
        deadlockedThread2.interrupt();
    }

    @Test
    void whenJvmDoesntSupportSynchronizerUsage_JvmThreadsDeadlockedMetricShouldNotBeRegistered() {
        try (MockedStatic<ManagementFactory> mockedStatic = Mockito.mockStatic(ManagementFactory.class)) {
            ThreadMXBean threadBean = mock(ThreadMXBean.class);
            when(threadBean.isSynchronizerUsageSupported()).thenReturn(false);
            mockedStatic.when(ManagementFactory::getThreadMXBean).thenReturn(threadBean);
            new JvmThreadDeadlockMetrics().bindTo(registry);

            // synchronizer usage is not monitored, so this should not be registered
            assertThat(registry.find("jvm.threads.deadlocked").gauge()).isNull();
            // but this one is still supported
            assertThat(registry.find("jvm.threads.deadlocked.monitor").gauge()).isNotNull();
        }
    }

    @Test
    void getDeadlockedThreadCountWhenFindDeadlockedThreadsIsNullShouldWork() {
        ThreadMXBean threadBean = mock(ThreadMXBean.class);
        when(threadBean.findDeadlockedThreads()).thenReturn(null);
        assertThat(JvmThreadDeadlockMetrics.getDeadlockedThreadCount(threadBean)).isEqualTo(0);
    }

    @Test
    void getDeadlockedThreadCountWhenFindMonitorDeadlockedThreadsIsNullShouldWork() {
        ThreadMXBean threadBean = mock(ThreadMXBean.class);
        when(threadBean.findMonitorDeadlockedThreads()).thenReturn(null);
        assertThat(JvmThreadDeadlockMetrics.getDeadlockedMonitorThreadCount(threadBean)).isEqualTo(0);
    }

    private static class DeadlockedThread {

        private final Thread thread;

        DeadlockedThread(CountDownLatch lock1IsLocked, Lock lock1, CountDownLatch lock2IsLocked, Lock lock2) {
            this.thread = new Thread(() -> {
                try {
                    lock1.lock();
                    lock1IsLocked.countDown();
                    lock2IsLocked.await();
                    lock2.lockInterruptibly();
                }
                catch (InterruptedException ignored) {
                }
            });
        }

        void start() {
            thread.start();
        }

        void interrupt() {
            thread.interrupt();
        }

    }

}
