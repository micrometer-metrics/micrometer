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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.micrometer.core.instrument.Statistic.Count;
import static io.micrometer.core.instrument.Statistic.Value;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class ExecutorServiceMetricsTest {
    private MeterRegistry registry;

    @BeforeEach
    void before() {
        registry = new SimpleMeterRegistry();
    }

    @DisplayName("thread pool executor can be instrumented after being initialized")
    @Test
    void threadPoolExecutor() {
        ExecutorService exec = Executors.newFixedThreadPool(2);
        ExecutorServiceMetrics.monitor(registry, exec, "exec");
        assertThreadPoolExecutorMetrics();
    }

    @DisplayName("scheduled thread pool executor can be instrumented after being initialized")
    @Test
    void scheduledThreadPoolExecutor() {
        ExecutorService exec = Executors.newScheduledThreadPool(2);
        ExecutorServiceMetrics.monitor(registry, exec, "exec");
        assertThreadPoolExecutorMetrics();
    }

    @DisplayName("ExecutorService can be monitored with a default set of metrics")
    @Test
    void monitorExecutorService() throws InterruptedException {
        ExecutorService pool = ExecutorServiceMetrics.monitor(registry, Executors.newSingleThreadExecutor(), "beep.pool");
        CountDownLatch taskStart = new CountDownLatch(1);
        CountDownLatch taskComplete = new CountDownLatch(1);

        pool.submit(() -> {
            taskStart.countDown();
            taskComplete.await(1, TimeUnit.SECONDS);
            System.out.println("beep");
            return 0;
        });
        pool.submit(() -> System.out.println("boop"));

        taskStart.await(1, TimeUnit.SECONDS);
        assertThat(registry.find("beep.pool.queued").value(Value, 1.0).gauge()).isPresent();

        taskComplete.countDown();
        pool.awaitTermination(1, TimeUnit.SECONDS);

        assertThat(registry.find("beep.pool").value(Count, 2.0).timer()).isPresent();
        assertThat(registry.find("beep.pool.queued").value(Value, 0.0).gauge()).isPresent();
    }

    private void assertThreadPoolExecutorMetrics() {
        assertThat(registry.find("exec.completed").meter()).isPresent();
        assertThat(registry.find("exec.queued").gauge()).isPresent();
        assertThat(registry.find("exec.pool").gauge()).isPresent();
    }
}
