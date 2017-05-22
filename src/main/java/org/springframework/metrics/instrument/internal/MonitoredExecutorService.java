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
package org.springframework.metrics.instrument.internal;

import org.springframework.metrics.instrument.Clock;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.Tag;
import org.springframework.metrics.instrument.Timer;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.stream.Collectors.toList;

/**
 * A metrics instrumented {@link java.util.concurrent.ExecutorService}.
 *
 * @author Jon Schneider
 */
public class MonitoredExecutorService implements ExecutorService {
    private final ExecutorService delegate;
    private final Clock clock;
    private final Timer timer;
    private final AtomicLong queued = new AtomicLong();

    public MonitoredExecutorService(MeterRegistry registry, ExecutorService delegate, String name, Iterable<Tag> tags) {
        this.delegate = delegate;
        this.clock = registry.getClock();
        this.timer = registry.timer(name + "_duration", tags);
        registry.gauge(name + "_queued", tags, queued);
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
        queued.incrementAndGet();
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        queued.incrementAndGet();
        return delegate.submit(wrap(task), result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        queued.incrementAndGet();
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        queued.accumulateAndGet(tasks.size(), (a, b) -> a + b);
        return delegate.invokeAll(wrapAll(tasks));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        queued.accumulateAndGet(tasks.size(), (a, b) -> a + b);
        return delegate.invokeAll(wrapAll(tasks), timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return delegate.invokeAny(wrapAll(tasks));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(wrapAll(tasks), timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        queued.incrementAndGet();
        delegate.execute(timer.wrap(command));
    }

    private <T> Collection<? extends Callable<T>> wrapAll(Collection<? extends Callable<T>> tasks) {
        return tasks.stream().map(this::wrap).collect(toList());
    }

    private <T> Callable<T> wrap(Callable<T> f) {
        return () -> {
            queued.decrementAndGet();
            final long s = clock.monotonicTime();
            try {
                return f.call();
            } finally {
                final long e = clock.monotonicTime();
                timer.record(e - s, TimeUnit.NANOSECONDS);
            }
        };
    }

    private Runnable wrap(Runnable f) {
        return () -> {
            queued.decrementAndGet();
            timer.record(f);
        };
    }
}
