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
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmThreadMeterConventions;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link JvmThreadMetrics}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
class JvmThreadMetricsTest {

    MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void threadMetrics() {
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
    void extraTagsAreApplied() {
        Tags extraTags = Tags.of("extra", "tag");
        new JvmThreadMetrics(extraTags).bindTo(registry);

        assertThat(registry.get("jvm.threads.live").tags(extraTags).gauge().value()).isPositive();
        assertThat(registry.get("jvm.threads.daemon").tags(extraTags).gauge().value()).isPositive();
        assertThat(registry.get("jvm.threads.peak").tags(extraTags).gauge().value()).isPositive();
        assertThat(registry.get("jvm.threads.states").tags(extraTags).tag("state", "runnable").gauge().value())
            .isPositive();
    }

    @Test
    void otelThreadStates() {
        new JvmThreadMetrics(Tags.empty(), new OpenTelemetryJvmThreadMeterConventions(Tags.empty())).bindTo(registry);
        double initialThreadCount = registry.get("jvm.threads.started").functionCounter().count();

        assertThat(registry.get("jvm.threads.live").gauge().value()).isPositive();
        assertThat(registry.get("jvm.threads.daemon").gauge().value()).isPositive();
        assertThat(registry.get("jvm.threads.peak").gauge().value()).isPositive();
        assertThat(registry.get("jvm.thread.count").tag("jvm.thread.state", "runnable").gauge().value()).isPositive();

        createBlockedThread();
        assertThat(registry.get("jvm.thread.count").tag("jvm.thread.state", "blocked").gauge().value()).isPositive();
        assertThat(registry.get("jvm.thread.count").tag("jvm.thread.state", "waiting").gauge().value()).isPositive();

        createTimedWaitingThread();
        assertThat(registry.get("jvm.thread.count").tag("jvm.thread.state", "timed_waiting").gauge().value())
            .isPositive();
        assertThat(registry.get("jvm.threads.started").functionCounter().count()).isGreaterThan(initialThreadCount);
    }

    @Test
    void otelThreadStatesWithExtraTags() {
        Tags extraTags = Tags.of("extra", "tag");
        new JvmThreadMetrics(extraTags, new OpenTelemetryJvmThreadMeterConventions(extraTags)).bindTo(registry);
        double initialThreadCount = registry.get("jvm.threads.started").tags(extraTags).functionCounter().count();

        assertThat(registry.get("jvm.threads.live").tags(extraTags).gauge().value()).isPositive();
        assertThat(registry.get("jvm.threads.daemon").tags(extraTags).gauge().value()).isPositive();
        assertThat(registry.get("jvm.threads.peak").tags(extraTags).gauge().value()).isPositive();
        assertThat(registry.get("jvm.thread.count").tags(extraTags).tag("jvm.thread.state", "runnable").gauge().value())
            .isPositive();

        createBlockedThread();
        assertThat(registry.get("jvm.thread.count").tags(extraTags).tag("jvm.thread.state", "blocked").gauge().value())
            .isPositive();
        assertThat(registry.get("jvm.thread.count").tags(extraTags).tag("jvm.thread.state", "waiting").gauge().value())
            .isPositive();

        createTimedWaitingThread();
        assertThat(registry.get("jvm.thread.count")
            .tags(extraTags)
            .tag("jvm.thread.state", "timed_waiting")
            .gauge()
            .value()).isPositive();
        assertThat(registry.get("jvm.threads.started").tags(extraTags).functionCounter().count())
            .isGreaterThan(initialThreadCount);
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

}
