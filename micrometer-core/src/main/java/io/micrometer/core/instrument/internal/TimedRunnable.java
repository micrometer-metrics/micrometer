/**
 * Copyright 2019 VMware, Inc.
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
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.lang.Nullable;

/**
 * A wrapper for a {@link Runnable} with idle and execution timings.
 */
class TimedRunnable implements Runnable {
    private final MeterRegistry registry;
    private final Timer executionTimer;
    private final Timer idleTimer;
    private final Runnable command;
    private final Timer.Sample idleSample;
    @Nullable
    private final Timer.Sample parentSample;

    TimedRunnable(MeterRegistry registry, Timer executionTimer, Timer idleTimer, Runnable command) {
        this.registry = registry;
        this.executionTimer = executionTimer;
        this.idleTimer = idleTimer;
        this.command = command;
        this.parentSample = registry.getCurrentSample();
        this.idleSample = Timer.start(registry);
        // should not be set to current sample after runnable completes
        registry.removeCurrentSample(this.idleSample);
    }

    @Override
    public void run() {
        idleSample.restore();
        idleSample.stop(idleTimer);
        if (parentSample != null)
            parentSample.restore();
        Timer.Sample executionSample = Timer.start(registry);
        try {
            command.run();
        } finally {
            executionSample.stop(executionTimer);
        }
    }
}
