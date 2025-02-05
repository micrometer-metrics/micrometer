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

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.awaitility.Awaitility.await;

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
    @ParameterizedTest
    @CsvSource({ "custom,custom.", "custom.,custom.", ",''", "' ',''" })
    void executor(String metricPrefix, String expectedMetricPrefix) throws InterruptedException {
        CountDownLatch lock = new CountDownLatch(1);
        Executor exec = r -> {
            r.run();
            lock.countDown();
        };
        Executor executor = monitorExecutorService("exec", metricPrefix, exec);
        executor.execute(() -> System.out.println("hello"));
        lock.await();

        assertThat(registry.get(expectedMetricPrefix + "executor.execution")
            .tags(userTags)
            .tag("name", "exec")
            .timer()
            .count()).isEqualTo(1L);
        assertThat(
                registry.get(expectedMetricPrefix + "executor.idle").tags(userTags).tag("name", "exec").timer().count())
            .isEqualTo(1L);
    }

    @DisplayName("ExecutorService is casted from Executor when necessary")
    @ParameterizedTest
    @CsvSource({ "custom,custom.", "custom.,custom.", ",''", "' ',''" })
    void executorCasting(String metricPrefix, String expectedMetricPrefix) {
        Executor exec = Executors.newFixedThreadPool(2);
        monitorExecutorService("exec", metricPrefix, exec);
        assertThreadPoolExecutorMetrics("exec", expectedMetricPrefix);
    }

    @DisplayName("thread pool executor can be instrumented after being initialized")
    @ParameterizedTest
    @CsvSource({ "custom,custom.", "custom.,custom.", ",''", "' ',''" })
    void threadPoolExecutor(String metricPrefix, String expectedMetricPrefix) {
        ExecutorService exec = Executors.newFixedThreadPool(2);
        monitorExecutorService("exec", metricPrefix, exec);
        assertThreadPoolExecutorMetrics("exec", expectedMetricPrefix);
    }

    @DisplayName("Scheduled thread pool executor can be instrumented after being initialized")
    @ParameterizedTest
    @CsvSource({ "custom,custom.", "custom.,custom.", ",''", "' ',''" })
    void scheduledThreadPoolExecutor(String metricPrefix, String expectedMetricPrefix) {
        ScheduledExecutorService exec = Executors.newScheduledThreadPool(2);
        monitorExecutorService("exec", metricPrefix, exec);
        assertThreadPoolExecutorMetrics("exec", expectedMetricPrefix);
    }

    @DisplayName("ScheduledExecutorService is casted from Executor when necessary")
    @ParameterizedTest
    @CsvSource({ "custom,custom.", "custom.,custom.", ",''", "' ',''" })
    void scheduledThreadPoolExecutorAsExecutor(String metricPrefix, String expectedMetricPrefix) {
        Executor exec = Executors.newScheduledThreadPool(2);
        monitorExecutorService("exec", metricPrefix, exec);
        assertThreadPoolExecutorMetrics("exec", expectedMetricPrefix);
    }

    @DisplayName("ScheduledExecutorService is casted from ExecutorService when necessary")
    @ParameterizedTest
    @CsvSource({ "custom,custom.", "custom.,custom.", ",''", "' ',''" })
    void scheduledThreadPoolExecutorAsExecutorService(String metricPrefix, String expectedMetricPrefix) {
        ExecutorService exec = Executors.newScheduledThreadPool(2);
        monitorExecutorService("exec", metricPrefix, exec);
        assertThreadPoolExecutorMetrics("exec", expectedMetricPrefix);
    }

    @DisplayName("ExecutorService can be monitored with a default set of metrics")
    @DisabledForJreRange(min = JRE.JAVA_16,
            disabledReason = "See gh-2317 for why we can't run this full test on Java 16+")
    @ParameterizedTest
    @CsvSource({ "custom,custom.", "custom.,custom.", ",''", "' ',''" })
    void monitorExecutorService(String metricPrefix, String expectedMetricPrefix) throws InterruptedException {
        ExecutorService pool = monitorExecutorService("beep.pool", metricPrefix, Executors.newSingleThreadExecutor());
        CountDownLatch taskStart = new CountDownLatch(1);
        CountDownLatch taskComplete = new CountDownLatch(1);

        pool.submit(() -> {
            taskStart.countDown();
            assertThat(taskComplete.await(1, TimeUnit.SECONDS)).isTrue();
            System.out.println("beep");
            return 0;
        });
        pool.submit(() -> System.out.println("boop"));

        assertThat(taskStart.await(1, TimeUnit.SECONDS)).isTrue();

        assertThat(registry.get(expectedMetricPrefix + "executor.queued")
            .tags(userTags)
            .tag("name", "beep.pool")
            .gauge()
            .value()).isEqualTo(1.0);

        taskComplete.countDown();
        await().untilAsserted(() -> {
            assertThat(registry.get(expectedMetricPrefix + "executor").tags(userTags).timer().count()).isEqualTo(2L);
            assertThat(registry.get(expectedMetricPrefix + "executor.idle").tags(userTags).timer().count())
                .isEqualTo(2L);
            assertThat(registry.get(expectedMetricPrefix + "executor.queued").tags(userTags).gauge().value())
                .isEqualTo(0.0);
        });

        pool.shutdown();
        assertThat(pool.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
    }

    @DisplayName("ExecutorService can be monitored with a default set of metrics after shutdown")
    @DisabledForJreRange(min = JRE.JAVA_16,
            disabledReason = "See gh-2317 for why we can't run this full test on Java 16+")
    @ParameterizedTest
    @CsvSource({ "custom,custom.", "custom.,custom.", ",''", "' ',''" })
    void monitorExecutorServiceAfterShutdown(String metricPrefix, String expectedMetricPrefix)
            throws InterruptedException {
        var exec = Executors.newFixedThreadPool(2);
        var monitorExecutorService = monitorExecutorService("exec", metricPrefix, exec);
        assertThreadPoolExecutorMetrics("exec", expectedMetricPrefix);
        assertThat(registry.get(expectedMetricPrefix + "executor.pool.core").tags(userTags).gauge().value())
            .isEqualTo(2L);

        monitorExecutorService.shutdownNow();
        assertThat(monitorExecutorService.awaitTermination(1, TimeUnit.SECONDS)).isTrue();

        exec = Executors.newFixedThreadPool(3);
        monitorExecutorService("exec", metricPrefix, exec);
        assertThreadPoolExecutorMetrics("exec", expectedMetricPrefix);

        assertThat(registry.get(expectedMetricPrefix + "executor.pool.core").tags(userTags).gauge().value())
            .isEqualTo(3L);

        exec.shutdown();
        assertThat(exec.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
    }

    @DisplayName("No exception thrown trying to monitor Executors private class")
    @Test
    @Issue("#2447") // Note: only reproduces on Java 16+ or with --illegal-access=deny
    void monitorExecutorsExecutorServicePrivateClass() {
        assertThatCode(() -> ExecutorServiceMetrics.monitor(registry, Executors.newSingleThreadExecutor(), ""))
            .doesNotThrowAnyException();
    }

    @DisplayName("ForkJoinPool is assigned with its own set of metrics")
    @ParameterizedTest
    @CsvSource({ "custom,custom.", "custom.,custom.", ",''", "' ',''" })
    void forkJoinPool(String metricPrefix, String expectedMetricPrefix) {
        var fjp = new ForkJoinPool(1);
        monitorExecutorService("fjp", metricPrefix, fjp);
        registry.get(expectedMetricPrefix + "executor.steals").tags(userTags).tag("name", "fjp").functionCounter();
        registry.get(expectedMetricPrefix + "executor.queued").tags(userTags).tag("name", "fjp").gauge();
        registry.get(expectedMetricPrefix + "executor.running").tags(userTags).tag("name", "fjp").gauge();
        registry.get(expectedMetricPrefix + "executor.parallelism").tags(userTags).tag("name", "fjp").gauge();
        registry.get(expectedMetricPrefix + "executor.pool.size").tags(userTags).tag("name", "fjp").gauge();
    }

    @DisplayName("ScheduledExecutorService can be monitored with a default set of metrics")
    @ParameterizedTest
    @CsvSource({ "custom,custom.", "custom.,custom.", ",''", "' ',''" })
    void monitorScheduledExecutorService(String metricPrefix, String expectedMetricPrefix)
            throws TimeoutException, ExecutionException, InterruptedException {
        ScheduledExecutorService pool = monitorExecutorService("scheduled.pool", metricPrefix,
                Executors.newScheduledThreadPool(2));

        CountDownLatch callableTaskStart = new CountDownLatch(1);
        CountDownLatch runnableTaskStart = new CountDownLatch(1);
        CountDownLatch callableTaskComplete = new CountDownLatch(1);
        CountDownLatch runnableTaskComplete = new CountDownLatch(1);

        Callable<Integer> scheduledBeepCallable = () -> {
            callableTaskStart.countDown();
            assertThat(callableTaskComplete.await(1, TimeUnit.SECONDS)).isTrue();
            return 1;
        };
        ScheduledFuture<Integer> callableResult = pool.schedule(scheduledBeepCallable, 10, TimeUnit.MILLISECONDS);

        Runnable scheduledBeepRunnable = () -> {
            runnableTaskStart.countDown();
            try {
                assertThat(runnableTaskComplete.await(1, TimeUnit.SECONDS)).isTrue();
            }
            catch (InterruptedException e) {
                throw new IllegalStateException("scheduled runnable interrupted before completion");
            }
        };
        ScheduledFuture<?> runnableResult = pool.schedule(scheduledBeepRunnable, 15, TimeUnit.MILLISECONDS);

        assertThat(registry.get(expectedMetricPrefix + "executor.scheduled.once")
            .tags(userTags)
            .tag("name", "scheduled.pool")
            .counter()
            .count()).isEqualTo(2);

        assertThat(callableTaskStart.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(runnableTaskStart.await(1, TimeUnit.SECONDS)).isTrue();

        callableTaskComplete.countDown();
        runnableTaskComplete.countDown();

        pool.shutdown();
        assertThat(pool.awaitTermination(1, TimeUnit.SECONDS)).isTrue();

        assertThat(callableResult.get(1, TimeUnit.MINUTES)).isEqualTo(1);
        assertThat(runnableResult.get(1, TimeUnit.MINUTES)).isNull();

        assertThat(registry.get(expectedMetricPrefix + "executor").tags(userTags).timer().count()).isEqualTo(2L);
        assertThat(registry.get(expectedMetricPrefix + "executor.idle").tags(userTags).timer().count()).isEqualTo(0L);
    }

    @DisplayName("ScheduledExecutorService repetitive tasks can be monitored with a default set of metrics")
    @ParameterizedTest
    @CsvSource({ "custom,custom.", "custom.,custom.", ",''", "' ',''" })
    void monitorScheduledExecutorServiceWithRepetitiveTasks(String metricPrefix, String expectedMetricPrefix)
            throws InterruptedException {
        ScheduledExecutorService pool = monitorExecutorService("scheduled.pool", metricPrefix,
                Executors.newScheduledThreadPool(1));
        CountDownLatch fixedRateInvocations = new CountDownLatch(3);
        CountDownLatch fixedDelayInvocations = new CountDownLatch(3);

        assertThat(
                registry.get(expectedMetricPrefix + "executor.scheduled.repetitively").tags(userTags).counter().count())
            .isEqualTo(0);
        assertThat(registry.get(expectedMetricPrefix + "executor").tags(userTags).timer().count()).isEqualTo(0L);

        Runnable repeatedAtFixedRate = () -> {
            fixedRateInvocations.countDown();
            if (fixedRateInvocations.getCount() == 0) {
                throw new RuntimeException("finished execution");
            }
        };
        pool.scheduleAtFixedRate(repeatedAtFixedRate, 10, 10, TimeUnit.MILLISECONDS);

        Runnable repeatedWithFixedDelay = () -> {
            fixedDelayInvocations.countDown();
            if (fixedDelayInvocations.getCount() == 0) {
                throw new RuntimeException("finished execution");
            }
        };
        pool.scheduleWithFixedDelay(repeatedWithFixedDelay, 5, 15, TimeUnit.MILLISECONDS);

        assertThat(
                registry.get(expectedMetricPrefix + "executor.scheduled.repetitively").tags(userTags).counter().count())
            .isEqualTo(2);

        assertThat(fixedRateInvocations.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(fixedDelayInvocations.await(5, TimeUnit.SECONDS)).isTrue();

        pool.shutdown();
        assertThat(pool.awaitTermination(1, TimeUnit.SECONDS)).isTrue();

        assertThat(registry.get(expectedMetricPrefix + "executor").tags(userTags).timer().count()).isEqualTo(6L);
        assertThat(registry.get(expectedMetricPrefix + "executor.idle").tags(userTags).timer().count()).isEqualTo(0L);
    }

    @Test
    @Issue("#5650")
    void queuedSubmissionsAreIncludedInExecutorQueuedMetric() {
        ForkJoinPool pool = new ForkJoinPool(1, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, false, 1, 1, 1,
                a -> true, 555, TimeUnit.MILLISECONDS);
        ExecutorServiceMetrics.monitor(registry, pool, "myForkJoinPool");
        AtomicBoolean busy = new AtomicBoolean(true);

        // will be an active task
        pool.execute(() -> {
            while (busy.get()) {
            }
        });

        // will be queued for submission
        pool.execute(() -> {
        });
        pool.execute(() -> {
        });

        double queued = registry.get("executor.queued").tag("name", "myForkJoinPool").gauge().value();
        busy.set(false);

        assertThat(queued).isEqualTo(2.0);

        pool.shutdown();
    }

    @SuppressWarnings("unchecked")
    private <T extends Executor> T monitorExecutorService(String executorName, String metricPrefix, T exec) {
        if (metricPrefix == null) {
            return (T) ExecutorServiceMetrics.monitor(registry, exec, executorName, userTags);
        }
        else {
            return (T) ExecutorServiceMetrics.monitor(registry, exec, executorName, metricPrefix, userTags);
        }
    }

    private void assertThreadPoolExecutorMetrics(String executorName, String metricPrefix) {
        registry.get(metricPrefix + "executor.completed").tags(userTags).tag("name", executorName).meter();
        registry.get(metricPrefix + "executor.queued").tags(userTags).tag("name", executorName).gauge();
        registry.get(metricPrefix + "executor.queue.remaining").tags(userTags).tag("name", executorName).gauge();
        registry.get(metricPrefix + "executor.active").tags(userTags).tag("name", executorName).gauge();
        registry.get(metricPrefix + "executor.pool.size").tags(userTags).tag("name", executorName).gauge();
        registry.get(metricPrefix + "executor.pool.core").tags(userTags).tag("name", executorName).gauge();
        registry.get(metricPrefix + "executor.pool.max").tags(userTags).tag("name", executorName).gauge();
        registry.get(metricPrefix + "executor.idle").tags(userTags).tag("name", executorName).timer();
        registry.get(metricPrefix + "executor").tags(userTags).tag("name", executorName).timer();
    }

    @Test
    void newSingleThreadScheduledExecutor() {
        String executorServiceName = "myExecutorService";
        ExecutorServiceMetrics.monitor(registry, Executors.newSingleThreadScheduledExecutor(), executorServiceName);
        // timer metrics still available, even on Java 16+
        registry.get("executor").tag("name", executorServiceName).timer();
        if (isJava16OrLater())
            return; // see gh-2317; ExecutorServiceMetrics not available for inaccessible
                    // JDK internal types
        registry.get("executor.completed").tag("name", executorServiceName).functionCounter();
    }

    private boolean isJava16OrLater() {
        return JRE.currentVersion().compareTo(JRE.JAVA_16) >= 0;
    }

    @Test
    void newSingleThreadScheduledExecutorWhenReflectiveAccessIsDisabled() {
        String executorServiceName = "myExecutorService";
        ExecutorServiceMetrics.disableIllegalReflectiveAccess();
        ExecutorServiceMetrics.monitor(registry, Executors.newSingleThreadScheduledExecutor(), executorServiceName);
        registry.get("executor").tag("name", executorServiceName).timer();
        assertThatThrownBy(() -> registry.get("executor.completed").tag("name", executorServiceName).functionCounter())
            .isExactlyInstanceOf(MeterNotFoundException.class);
    }

}
