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
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.internal.TimedExecutorService;

import java.lang.reflect.Field;
import java.util.concurrent.*;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

/**
 * Monitors the status of executor service pools. Does not record timings on operations executed in the {@link ExecutorService},
 * as this requires the instance to be wrapped. Timings are provided separately by wrapping the executor service
 * with {@link TimedExecutorService}.
 *
 * @author Jon Schneider
 * @author Clint Checketts
 */
public class ExecutorServiceMetrics implements MeterBinder {
    /**
     * Record metrics on the use of an {@link Executor}.
     *
     * @param registry The registry to bind metrics to.
     * @param executor The executor to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented executor, proxied.
     */
    public static Executor monitor(MeterRegistry registry, Executor executor, String name, Iterable<Tag> tags) {
        final Timer commandTimer = registry.timer(name, tags);
        return commandTimer::record;
    }

    /**
     * Record metrics on the use of an {@link Executor}.
     *
     * @param registry The registry to bind metrics to.
     * @param executor The executor to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented executor, proxied.
     */
    public static Executor monitor(MeterRegistry registry, Executor executor, String name, Tag... tags) {
        return monitor(registry, executor, name, asList(tags));
    }

    /**
     * Record metrics on the use of an {@link ExecutorService}.
     *
     * @param registry The registry to bind metrics to.
     * @param executor The executor to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented executor, proxied.
     */
    public static ExecutorService monitor(MeterRegistry registry, ExecutorService executor, String name, Iterable<Tag> tags) {
        new ExecutorServiceMetrics(executor, name, tags).bindTo(registry);
        return new TimedExecutorService(registry, executor, name, tags);
    }

    /**
     * Record metrics on the use of an {@link ExecutorService}.
     *
     * @param registry The registry to bind metrics to.
     * @param executor The executor to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented executor, proxied.
     */
    public static ExecutorService monitor(MeterRegistry registry, ExecutorService executor, String name, Tag... tags) {
        return monitor(registry, executor, name, asList(tags));
    }

    private final ExecutorService executorService;
    private final String name;
    private final Iterable<Tag> tags;

    public ExecutorServiceMetrics(ExecutorService executorService, String name, Iterable<Tag> tags) {
        this.name = name;
        this.tags = tags;
        this.executorService = executorService;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        if (executorService == null) {
            return;
        }

        String className = executorService.getClass().getName();

        if (executorService instanceof ThreadPoolExecutor) {
            monitor(registry, (ThreadPoolExecutor) executorService);
        } else if (className.equals("java.util.concurrent.Executors$DelegatedScheduledExecutorService")) {
            monitor(registry, unwrapThreadPoolExecutor(executorService, executorService.getClass()));
        } else if (className.equals("java.util.concurrent.Executors$FinalizableDelegatedExecutorService")) {
            monitor(registry, unwrapThreadPoolExecutor(executorService, executorService.getClass().getSuperclass()));
        } else if (executorService instanceof ForkJoinPool) {
            monitor(registry, (ForkJoinPool) executorService);
        }
    }

    /**
     * Every ScheduledThreadPoolExecutor created by {@link Executors} is wrapped. Also,
     * {@link Executors#newSingleThreadExecutor()} wrap a regular {@link ThreadPoolExecutor}.
     */
    private ThreadPoolExecutor unwrapThreadPoolExecutor(ExecutorService executor, Class<?> wrapper) {
        try {
            Field e = wrapper.getDeclaredField("e");
            e.setAccessible(true);
            return (ThreadPoolExecutor) e.get(executorService);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Do nothing. We simply can't get to the underlying ThreadPoolExecutor.
        }
        return null;
    }

    private void monitor(MeterRegistry registry, ThreadPoolExecutor tp) {
        if (tp == null) {
            return;
        }

        registry.more().counter(registry.createId(name + ".completed", tags, null,
                "The approximate total number of tasks that have completed execution"),
            tp, ThreadPoolExecutor::getCompletedTaskCount);

        registry.gauge(registry.createId(name + ".active", tags, null,
            "The approximate number of threads that are actively executing tasks"),
            tp, ThreadPoolExecutor::getActiveCount);

        registry.gauge(registry.createId(name + ".queued", tags,
            "The approximate number of threads that are queued for execution"),
            tp, tpRef -> tpRef.getQueue().size());

        registry.gauge(registry.createId(name + ".pool", tags,
            "The current number of threads in the pool"),
        tp, ThreadPoolExecutor::getPoolSize);
    }

    private void monitor(MeterRegistry registry, ForkJoinPool fj) {
        registry.more().counter(registry.createId(name + ".steals", emptyList(),
            "Estimate of the total number of tasks stolen from " +
                "one thread's work queue by another. The reported value " +
                "underestimates the actual total number of steals when the pool " +
                "is not quiescent"), fj, ForkJoinPool::getStealCount);

        registry.gauge(registry.createId(name + ".queued", tags,
            "An estimate of the total number of tasks currently held in queues by worker threads"),
            fj, ForkJoinPool::getQueuedTaskCount);

        registry.gauge(registry.createId(name + ".active", tags,
            "An estimate of the number of threads that are currently stealing or executing tasks"),
            fj, ForkJoinPool::getActiveThreadCount);

        registry.gauge(registry.createId(name + ".running", tags,
            "An estimate of the number of worker threads that are not blocked waiting to join tasks or for other managed synchronization",
            "threads"), fj, ForkJoinPool::getRunningThreadCount);
    }
}
