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
package io.micrometer.spring.async;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * A {@link ThreadPoolTaskExecutor} which is timed
 *
 * @author David Held
 */
@NonNullApi
@NonNullFields
public class TimedThreadPoolTaskExecutor extends ThreadPoolTaskExecutor {
    private final MeterRegistry registry;
    private final String name;
    private final Iterable<Tag> tags;
    private final Timer timer;

    public TimedThreadPoolTaskExecutor(MeterRegistry registry, String name, Iterable<Tag> tags) {
        this.registry = registry;
        this.name = name;
        this.tags = tags;
        this.timer = registry.timer(name, tags);
    }

    @Override
    public void initialize() {
        super.initialize();
        new ThreadPoolTaskExecutorMetrics(this, name, tags).bindTo(registry);
    }

    @Override
    public void execute(Runnable task) {
        super.execute(timer.wrap(task));
    }

    @Override
    public void execute(Runnable task, long startTimeout) {
        super.execute(timer.wrap(task), startTimeout);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return super.submit(timer.wrap(task));
    }

    @Override
    public Future<?> submit(Runnable task) {
        return super.submit(timer.wrap(task));
    }

    @Override
    public <T> ListenableFuture<T> submitListenable(Callable<T> task) {
        return super.submitListenable(timer.wrap(task));
    }

    @Override
    public ListenableFuture<?> submitListenable(Runnable task) {
        return super.submitListenable(timer.wrap(task));
    }
}
