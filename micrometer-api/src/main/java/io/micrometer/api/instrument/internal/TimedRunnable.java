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
package io.micrometer.api.instrument.internal;

import io.micrometer.api.instrument.MeterRegistry;
import io.micrometer.api.instrument.Timer;

/**
 * A wrapper for a {@link Runnable} with idle and execution timings.
 */
class TimedRunnable implements Runnable {
    private final MeterRegistry registry;
    private final Timer executionTimer;
    private final Timer idleTimer;
    private final Runnable command;
    private final Timer.Sample idleSample;
//    private final Timer.Scope idleScope;

    TimedRunnable(MeterRegistry registry, Timer executionTimer, Timer idleTimer, Runnable command) {
        this.registry = registry;
        this.executionTimer = executionTimer;
        this.idleTimer = idleTimer;
        this.command = command;
        this.idleSample = Timer.start(registry);
//        this.idleScope = this.idleSample.makeCurrent();
    }

    @Override
    public void run() {
//        idleScope.close();
        idleSample.stop(idleTimer);
        Timer.Sample executionSample = Timer.start(registry);
//        try (Timer.Scope scope = executionSample.makeCurrent()) {
        try {
            command.run();
        }
        finally {
            executionSample.stop(executionTimer);
        }
    }
}
