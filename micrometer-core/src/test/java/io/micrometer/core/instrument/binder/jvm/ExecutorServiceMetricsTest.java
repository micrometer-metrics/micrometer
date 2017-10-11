/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.jvm;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static io.micrometer.core.instrument.Statistic.Count;
import static io.micrometer.core.instrument.Statistic.Value;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class ExecutorServiceMetricsTest {
    private MeterRegistry registry;

    private Iterable<Tag> userTags = Tags.zip("userTagKey", "userTagValue");

    @BeforeEach
    void before() {
        registry = new SimpleMeterRegistry();
    }

    @DisplayName("Normal executor can be instrumented after being initialized")
    @Test
    void executor() throws InterruptedException {
        CountDownLatch lock = new CountDownLatch(1);
        Executor exec = r -> {
            r.run();
            lock.countDown();
        };
        Executor executor = ExecutorServiceMetrics.monitor(registry, exec, "exec", userTags);
        executor.execute(() -> System.out.println("hello"));
        lock.await();
        assertThat(registry.find("exec").tags(userTags).timer()).map(Timer::count).hasValue(1L);
    }

    @DisplayName("ExecutorService is casted from Executor when necessary")
    @Test
    void executorCasting() {
        Executor exec = Executors.newFixedThreadPool(2);
        ExecutorServiceMetrics.monitor(registry, exec, "exec", userTags);
        assertThreadPoolExecutorMetrics();
    }

    @DisplayName("thread pool executor can be instrumented after being initialized")
    @Test
    void threadPoolExecutor() {
        ExecutorService exec = Executors.newFixedThreadPool(2);
        ExecutorServiceMetrics.monitor(registry, exec, "exec", userTags);
        assertThreadPoolExecutorMetrics();
    }

    @DisplayName("scheduled thread pool executor can be instrumented after being initialized")
    @Test
    void scheduledThreadPoolExecutor() {
        ExecutorService exec = Executors.newScheduledThreadPool(2);
        ExecutorServiceMetrics.monitor(registry, exec, "exec", userTags);
        assertThreadPoolExecutorMetrics();
    }

    @DisplayName("ExecutorService can be monitored with a default set of metrics")
    @Test
    void monitorExecutorService() throws InterruptedException {
        ExecutorService pool = ExecutorServiceMetrics.monitor(registry, Executors.newSingleThreadExecutor(), "beep.pool", userTags);
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
        assertThat(registry.find("beep.pool.queued").tags(userTags).value(Value, 1.0).gauge()).isPresent();

        taskComplete.countDown();
        pool.awaitTermination(1, TimeUnit.SECONDS);

        assertThat(registry.find("beep.pool").tags(userTags).value(Count, 2.0).timer()).isPresent();
        assertThat(registry.find("beep.pool.queued").tags(userTags).value(Value, 0.0).gauge()).isPresent();
    }

    private void assertThreadPoolExecutorMetrics() {
        assertThat(registry.find("exec.completed").tags(userTags).meter()).isPresent();
        assertThat(registry.find("exec.queued").tags(userTags).gauge()).isPresent();
        assertThat(registry.find("exec.active").tags(userTags).gauge()).isPresent();
        assertThat(registry.find("exec.pool").tags(userTags).gauge()).isPresent();
        assertThat(registry.find("exec").tags(userTags).timer()).isPresent();
    }
}
