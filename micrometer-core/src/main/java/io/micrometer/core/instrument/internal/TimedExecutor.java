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
package io.micrometer.core.instrument.internal;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.Executor;

/**
 * An {@link Executor} that is timed
 */
public class TimedExecutor implements Executor {
    private final MeterRegistry registry;
    private final Executor delegate;
    private final Timer executionTimer;
    private final Timer idleTimer;

    public TimedExecutor(MeterRegistry registry, Executor delegate, String executorName, Iterable<Tag> tags) {
        this.registry = registry;
        this.delegate = delegate;
        this.executionTimer = registry.timer("executor.execution", Tags.concat(tags, "name", executorName));
        this.idleTimer = registry.timer("executor.idle", Tags.concat(tags, "name", executorName));
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(new TimedRunnable(command));
    }

    class TimedRunnable implements Runnable {

        private final Runnable command;
        private final Timer.Sample idleSample;

        public TimedRunnable(Runnable command) {
            this.command = command;
            this.idleSample = Timer.start(registry);
        }

        @Override
        public void run() {
            idleSample.stop(idleTimer);
            Timer.Sample executionSample = Timer.start(registry);
            try {
                command.run();
            } finally {
                executionSample.stop(executionTimer);
            }
        }

    }


}
