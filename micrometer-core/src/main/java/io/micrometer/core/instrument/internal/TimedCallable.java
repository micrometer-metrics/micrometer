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
package io.micrometer.core.instrument.internal;

import java.util.concurrent.Callable;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * An {@link Callable} that is timed
 */
public final class TimedCallable<V> implements Callable<V> {

    private final Callable<V> command;
    private final MeterRegistry registry;
    private final Timer.Sample idleSample;
    private final Timer idleTimer;
    private final Timer executionTimer;

    TimedCallable(Callable<V> command, MeterRegistry registry, Timer idleTimer,
            Timer executionTimer) {
        this.command = command;
        this.registry = registry;
        this.idleTimer = idleTimer;
        this.executionTimer = executionTimer;
        this.idleSample = Timer.start(registry);
    }

    @Override
    public V call() throws Exception {

        idleSample.stop(idleTimer);
        final Timer.Sample executionSample = Timer.start(registry);

        V result = null;
        try {
            result = command.call();
        } finally {
            executionSample.stop(executionTimer);
        }
        return result;
    }

}
