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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link JvmThreadMetrics}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
class JvmThreadMetricsTest {

    @Test
    void threadMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        new JvmThreadMetrics().bindTo(registry);
        double initialThreadCount = registry.get("jvm.threads.started").functionCounter().count();

        assertThat(registry.get("jvm.threads.live").gauge().value()).isPositive();
        assertThat(registry.get("jvm.threads.daemon").gauge().value()).isPositive();
        assertThat(registry.get("jvm.threads.peak").gauge().value()).isPositive();
        assertThat(registry.get("jvm.threads.states").tag("state", "runnable").gauge().value()).isPositive();

        createBlockedThread();
        assertThat(registry.get("jvm.threads.states").tag("state", "blocked").gauge().value()).isPositive();
        assertThat(registry.get("jvm.threads.states").tag("state", "waiting").gauge().value()).isPositive();

        createTimedWaitingThread();
        assertThat(registry.get("jvm.threads.states").tag("state", "timed-waiting").gauge().value()).isPositive();
        assertThat(registry.get("jvm.threads.started").functionCounter().count()).isGreaterThan(initialThreadCount);

        assertThat(registry.get("jvm.threads.deadlocked").gauge().value()).isEqualTo(0);
        assertThat(registry.get("jvm.threads.deadlocked.monitor").gauge().value()).isEqualTo(0);
        createTemporarilyDeadlockedThread();
        assertThat(registry.get("jvm.threads.deadlocked").gauge().value()).isEqualTo(2);
        assertThat(registry.get("jvm.threads.deadlocked.monitor").gauge().value()).isEqualTo(2);
    }

    @Test
    void getThreadStateCountWhenThreadInfoIsNullShouldWork() {
        ThreadMXBean threadBean = mock(ThreadMXBean.class);
        long[] threadIds = { 1L, 2L };
        when(threadBean.getAllThreadIds()).thenReturn(threadIds);
        ThreadInfo threadInfo = mock(ThreadInfo.class);
        when(threadInfo.getThreadState()).thenReturn(Thread.State.RUNNABLE);
        when(threadBean.getThreadInfo(threadIds)).thenReturn(new ThreadInfo[] { threadInfo, null });
        assertThat(JvmThreadMetrics.getThreadStateCount(threadBean, Thread.State.RUNNABLE)).isEqualTo(1);
    }

    @Test
    void getDeadlockedThreadCountWhenFindDeadlockedThreadsIsNullShouldWork() {
        ThreadMXBean threadBean = mock(ThreadMXBean.class);
        when(threadBean.findDeadlockedThreads()).thenReturn(null);
        assertThat(JvmThreadMetrics.getDeadlockedThreadCount(threadBean)).isEqualTo(0);
    }

    @Test
    void getDeadlockedThreadCountWhenFindMonitorDeadlockedThreadsIsNullShouldWork() {
        ThreadMXBean threadBean = mock(ThreadMXBean.class);
        when(threadBean.findMonitorDeadlockedThreads()).thenReturn(null);
        assertThat(JvmThreadMetrics.getDeadlockedMonitorThreadCount(threadBean)).isEqualTo(0);
    }

    private void createTimedWaitingThread() {
        new Thread(() -> {
            sleep(5);
        }).start();
        sleep(1);
    }

    private void sleep(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        }
        catch (InterruptedException ignored) {
        }
    }

    private void createBlockedThread() {
        Object lock = new Object();
        new Thread(() -> {
            synchronized (lock) {
                sleep(5);
            }
        }).start();
        new Thread(() -> {
            synchronized (lock) {
                sleep(5);
            }
        }).start();
        sleep(1);
    }

    private void createTemporarilyDeadlockedThread() {
        // create two threads that are deadlocked for about 5 seconds

        Object lock1 = new Object();
        Object lock2 = new Object();

        int initialSleepSeconds = 1;
        int deadlockedSleepSeconds = 5;
        new Thread(() -> {
            synchronized (lock1) {
                sleep(initialSleepSeconds);
                synchronized (lock2) {
                    sleep(deadlockedSleepSeconds);
                }
            }
        }).start();
        new Thread(() -> {
            synchronized (lock2) {
                sleep(initialSleepSeconds);
                synchronized (lock1) {
                    sleep(deadlockedSleepSeconds);
                }
            }
        }).start();
        sleep(initialSleepSeconds + 2);
    }

}
