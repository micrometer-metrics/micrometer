/*
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.core.instrument.internal;

import io.micrometer.core.instrument.*;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

import static java.util.stream.Collectors.toList;

/**
 * A {@link ScheduledExecutorService} that is timed. This class is for internal use.
 *
 * @author Sebastian Lövdahl
 * @since 1.3.0
 * @see io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics
 */
public class TimedScheduledExecutorService implements ScheduledExecutorService {

    private final MeterRegistry registry;

    private final ScheduledExecutorService delegate;

    private final Timer executionTimer;

    private final Timer idleTimer;

    private final Counter scheduledOnce;

    private final Counter scheduledRepetitively;

    public TimedScheduledExecutorService(MeterRegistry registry, ScheduledExecutorService delegate,
            String executorServiceName, String metricPrefix, Iterable<Tag> tags) {
        this.registry = registry;
        this.delegate = delegate;
        Tags finalTags = Tags.concat(tags, "name", executorServiceName);
        this.executionTimer = registry.timer(metricPrefix + "executor", finalTags);
        this.idleTimer = registry.timer(metricPrefix + "executor.idle", finalTags);
        this.scheduledOnce = registry.counter(metricPrefix + "executor.scheduled.once", finalTags);
        this.scheduledRepetitively = registry.counter(metricPrefix + "executor.scheduled.repetitively", finalTags);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(wrap(task), result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return delegate.invokeAll(wrapAll(tasks));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.invokeAll(wrapAll(tasks), timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return delegate.invokeAny(wrapAll(tasks));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(wrapAll(tasks), timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(wrap(command));
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        scheduledOnce.increment();
        return delegate.schedule(executionTimer.wrap(command), delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        scheduledOnce.increment();
        return delegate.schedule(executionTimer.wrap(callable), delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        scheduledRepetitively.increment();
        return delegate.scheduleAtFixedRate(executionTimer.wrap(command), initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        scheduledRepetitively.increment();
        return delegate.scheduleWithFixedDelay(executionTimer.wrap(command), initialDelay, delay, unit);
    }

    private Runnable wrap(Runnable task) {
        return new TimedRunnable(registry, executionTimer, idleTimer, task);
    }

    private <T> Callable<T> wrap(Callable<T> task) {
        return new TimedCallable<>(registry, executionTimer, idleTimer, task);
    }

    private <T> Collection<? extends Callable<T>> wrapAll(Collection<? extends Callable<T>> tasks) {
        return tasks.stream().map(this::wrap).collect(toList());
    }

}
