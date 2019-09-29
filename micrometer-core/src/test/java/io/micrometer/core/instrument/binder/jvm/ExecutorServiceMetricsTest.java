/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.jvm;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Tests for {@link ExecutorServiceMetrics}.
 *
 * @author Clint Checketts
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Sebastian Lövdahl
 */
class ExecutorServiceMetricsTest {
    private MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
    private Iterable<Tag> userTags = Tags.of("userTagKey", "userTagValue");

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

        assertThat(registry.get("executor.execution").tags(userTags).tag("name", "exec").timer().count()).isEqualTo(1L);
        assertThat(registry.get("executor.idle").tags(userTags).tag("name", "exec").timer().count()).isEqualTo(1L);
    }

    @DisplayName("ExecutorService is casted from Executor when necessary")
    @Test
    void executorCasting() {
        Executor exec = Executors.newFixedThreadPool(2);
        ExecutorServiceMetrics.monitor(registry, exec, "exec", userTags);
        assertThreadPoolExecutorMetrics("exec");
    }

    @DisplayName("thread pool executor can be instrumented after being initialized")
    @Test
    void threadPoolExecutor() {
        ExecutorService exec = Executors.newFixedThreadPool(2);
        ExecutorServiceMetrics.monitor(registry, exec, "exec", userTags);
        assertThreadPoolExecutorMetrics("exec");
    }

    @DisplayName("Scheduled thread pool executor can be instrumented after being initialized")
    @Test
    void scheduledThreadPoolExecutor() {
        ScheduledExecutorService exec = Executors.newScheduledThreadPool(2);
        ExecutorServiceMetrics.monitor(registry, exec, "exec", userTags);
        assertThreadPoolExecutorMetrics("exec");
    }

    @DisplayName("ScheduledExecutorService is casted from Executor when necessary")
    @Test
    void scheduledThreadPoolExecutorAsExecutor() {
        Executor exec = Executors.newScheduledThreadPool(2);
        ExecutorServiceMetrics.monitor(registry, exec, "exec", userTags);
        assertThreadPoolExecutorMetrics("exec");
    }

    @DisplayName("ScheduledExecutorService is casted from ExecutorService when necessary")
    @Test
    void scheduledThreadPoolExecutorAsExecutorService() {
        ExecutorService exec = Executors.newScheduledThreadPool(2);
        ExecutorServiceMetrics.monitor(registry, exec, "exec", userTags);
        assertThreadPoolExecutorMetrics("exec");
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
        assertThat(registry.get("executor.queued").tags(userTags).tag("name", "beep.pool")
                .gauge().value()).isEqualTo(1.0);

        taskComplete.countDown();
        pool.awaitTermination(1, TimeUnit.SECONDS);

        assertThat(registry.get("executor").tags(userTags).timer().count()).isEqualTo(2L);
        assertThat(registry.get("executor.idle").tags(userTags).timer().count()).isEqualTo(2L);
        assertThat(registry.get("executor.queued").tags(userTags).gauge().value()).isEqualTo(0.0);
    }

    @DisplayName("ScheduledExecutorService can be monitored with a default set of metrics")
    @Test
    void monitorScheduledExecutorService() throws TimeoutException, ExecutionException, InterruptedException {
        ScheduledExecutorService pool = ExecutorServiceMetrics.monitor(registry, Executors.newScheduledThreadPool(1), "scheduled.pool", userTags);
        CountDownLatch callableTaskStart = new CountDownLatch(1);
        CountDownLatch runnableTaskStart = new CountDownLatch(1);
        CountDownLatch callableTaskComplete = new CountDownLatch(1);
        CountDownLatch runnableTaskComplete = new CountDownLatch(1);

        Callable<Integer> scheduledBeepCallable = () -> {
            callableTaskStart.countDown();
            callableTaskComplete.await(1, TimeUnit.SECONDS);
            System.out.println("scheduled beep callable");
            return 1;
        };
        ScheduledFuture<Integer> callableResult = pool.schedule(scheduledBeepCallable, 10, TimeUnit.MILLISECONDS);

        Runnable scheduledBeepRunnable = () -> {
            runnableTaskStart.countDown();
            try {
                runnableTaskComplete.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new IllegalStateException("scheduled runnable interrupted before completion");
            }
            System.out.println("scheduled beep runnable");
        };
        ScheduledFuture<?> runnableResult = pool.schedule(scheduledBeepRunnable, 15, TimeUnit.MILLISECONDS);

        assertThat(registry.get("executor.scheduled.once").tags(userTags).tag("name", "scheduled.pool").counter().count()).isEqualTo(2);

        callableTaskStart.await(1, TimeUnit.SECONDS);
        runnableTaskComplete.await(1, TimeUnit.SECONDS);

        callableTaskComplete.countDown();
        runnableTaskComplete.countDown();
        pool.awaitTermination(1, TimeUnit.SECONDS);

        assertThat(callableResult.get(1, TimeUnit.MINUTES)).isEqualTo(1);
        assertThat(runnableResult.get(1, TimeUnit.MINUTES)).isNull();

        assertThat(registry.get("executor").tags(userTags).timer().count()).isEqualTo(2L);
        assertThat(registry.get("executor.idle").tags(userTags).timer().count()).isEqualTo(0L);
    }

    @DisplayName("ScheduledExecutorService repetitive tasks can be monitored with a default set of metrics")
    @Test
    void monitorScheduledExecutorServiceWithRepetitiveTasks() throws InterruptedException {
        ScheduledExecutorService pool = ExecutorServiceMetrics.monitor(registry, Executors.newScheduledThreadPool(1), "scheduled.pool", userTags);
        CountDownLatch fixedRateInvocations = new CountDownLatch(3);
        CountDownLatch fixedDelayInvocations = new CountDownLatch(3);

        assertThat(registry.get("executor.scheduled.repetitively").tags(userTags).counter().count()).isEqualTo(0);
        assertThat(registry.get("executor").tags(userTags).timer().count()).isEqualTo(0L);

        Runnable repeatedAtFixedRate = () -> {
            fixedRateInvocations.countDown();
            if (fixedRateInvocations.getCount() == 0) {
                throw new RuntimeException("finished execution");
            }
            System.out.println("fixed rate beep runnable");
        };
        pool.scheduleAtFixedRate(repeatedAtFixedRate, 10, 10, TimeUnit.MILLISECONDS);

        Runnable repeatedWithFixedDelay = () -> {
            fixedDelayInvocations.countDown();
            if (fixedDelayInvocations.getCount() == 0) {
                throw new RuntimeException("finished execution");
            }
            System.out.println("fixed delay beep runnable");
        };
        pool.scheduleWithFixedDelay(repeatedWithFixedDelay, 5, 15, TimeUnit.MILLISECONDS);

        assertThat(registry.get("executor.scheduled.repetitively").tags(userTags).counter().count()).isEqualTo(2);

        fixedRateInvocations.await(5, TimeUnit.SECONDS);
        fixedDelayInvocations.await(5, TimeUnit.SECONDS);

        pool.awaitTermination(1, TimeUnit.SECONDS);

        assertThat(registry.get("executor").tags(userTags).timer().count()).isEqualTo(6L);
        assertThat(registry.get("executor.idle").tags(userTags).timer().count()).isEqualTo(0L);
    }

    private void assertThreadPoolExecutorMetrics(String executorName) {
        registry.get("executor.completed").tags(userTags).tag("name", executorName).meter();
        registry.get("executor.queued").tags(userTags).tag("name", executorName).gauge();
        registry.get("executor.queue.remaining").tags(userTags).tag("name", executorName).gauge();
        registry.get("executor.active").tags(userTags).tag("name", executorName).gauge();
        registry.get("executor.pool.size").tags(userTags).tag("name", executorName).gauge();
        registry.get("executor.idle").tags(userTags).tag("name", executorName).timer();
        registry.get("executor").tags(userTags).tag("name", executorName).timer();
    }
}
