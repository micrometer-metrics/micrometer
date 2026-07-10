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
package io.micrometer.core.instrument.internal;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.Executor;

/**
 * An {@link Executor} that is timed. This class is for internal use.
 *
 * @see io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics
 */
public class TimedExecutor implements Executor {

    private final MeterRegistry registry;

    private final Executor delegate;

    private final Timer executionTimer;

    private final Timer idleTimer;

    public TimedExecutor(MeterRegistry registry, Executor delegate, String executorName, String metricPrefix,
            Iterable<Tag> tags) {
        this.registry = registry;
        this.delegate = delegate;
        Tags finalTags = Tags.concat(tags, "name", executorName);
        this.executionTimer = registry.timer(metricPrefix + "executor.execution", finalTags);
        this.idleTimer = registry.timer(metricPrefix + "executor.idle", finalTags);
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(new TimedRunnable(registry, executionTimer, idleTimer, command));
    }

}
