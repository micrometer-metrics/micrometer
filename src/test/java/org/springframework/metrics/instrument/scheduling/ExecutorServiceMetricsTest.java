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
package org.springframework.metrics.instrument.scheduling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.metrics.instrument.Gauge;
import org.springframework.metrics.instrument.Meter;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.Timer;
import org.springframework.metrics.instrument.simple.SimpleMeterRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.springframework.metrics.instrument.Meters.monitor;

/**
 * @author Jon Schneider
 * @author Clint Checketts
 */
class ExecutorServiceMetricsTest {
    private MeterRegistry registry;

    @BeforeEach
    void before() {
        registry = new SimpleMeterRegistry();
    }

    @DisplayName("thread pool task executor can be instrumented after being initialized")
    @Test
    void threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.initialize();

        monitor(registry, exec, "exec");
        assertThreadPoolExecutorMetrics("exec");
    }

    @DisplayName("thread pool task scheduler can be instrumented after being initialized")
    @Test
    void taskScheduler() {
        ThreadPoolTaskScheduler sched = new ThreadPoolTaskScheduler();
        sched.initialize();

        monitor(registry, sched, "sched");
        assertThreadPoolExecutorMetrics("sched");
    }

    @DisplayName("thread pool executor can be instrumented after being initialized")
    @Test
    void threadPoolExecutor() {
        ExecutorService exec = Executors.newFixedThreadPool(2);
        monitor(registry, exec, "exec");
        assertThreadPoolExecutorMetrics("exec");
    }

    @DisplayName("scheduled thread pool executor can be instrumented after being initialized")
    @Test
    void scheduledThreadPoolExecutor() {
        ExecutorService exec = Executors.newScheduledThreadPool(2);
        monitor(registry, exec, "exec");
        assertThreadPoolExecutorMetrics("exec");
    }

    @DisplayName("ExecutorService can be monitored with a default set of metrics")
    @Test
    void monitorExecutorService() throws InterruptedException {
        ExecutorService pool = monitor(registry, Executors.newSingleThreadExecutor(), "beep_pool");
        CountDownLatch taskStart = new CountDownLatch(1);
        CountDownLatch taskComplete = new CountDownLatch(1);

        pool.submit(() -> {
            taskStart.countDown();
            taskComplete.await();
            System.out.println("beep");
            return 0;
        });
        pool.submit(() -> System.out.println("boop"));

        taskStart.await();
        assertThat(registry.findMeter(Gauge.class, "beep_pool_queue_size"))
                .hasValueSatisfying(g -> assertThat(g.value()).isEqualTo(1, offset(1e-12)));

        taskComplete.countDown();
        pool.awaitTermination(1, TimeUnit.SECONDS);

        assertThat(registry.findMeter(Timer.class, "beep_pool_duration"))
                .hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(2));
        assertThat(registry.findMeter(Gauge.class, "beep_pool_queue_size"))
                .hasValueSatisfying(g -> assertThat(g.value()).isEqualTo(0, offset(1e-12)));
    }

    private void assertThreadPoolExecutorMetrics(String name) {
        assertThat(registry.findMeter(Meter.Type.Counter, name)).isPresent();
        assertThat(registry.findMeter(Gauge.class, name + "_queue_size")).isPresent();
        assertThat(registry.findMeter(Gauge.class, name + "_pool_size")).isPresent();
    }
}
