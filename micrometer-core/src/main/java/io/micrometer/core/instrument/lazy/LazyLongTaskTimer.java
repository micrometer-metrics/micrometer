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
package io.micrometer.core.instrument.lazy;

import io.micrometer.core.instrument.LongTaskTimer;

import java.util.function.Supplier;

public class LazyLongTaskTimer implements LongTaskTimer {
    private final Supplier<LongTaskTimer> timerBuilder;
    private volatile LongTaskTimer timer;

    private LongTaskTimer timer() {
        final LongTaskTimer result = timer;
        return result == null ? (timer == null ? timer = timerBuilder.get() : timer) : result;
    }

    public LazyLongTaskTimer(Supplier<LongTaskTimer> timerBuilder) {
        this.timerBuilder = timerBuilder;
    }

    @Override
    public Id getId() {
        return timer().getId();
    }

    @Override
    public String getDescription() {
        return timer().getDescription();
    }

    @Override
    public long start() {
        return timer().start();
    }

    @Override
    public long stop(long task) {
        return timer().stop(task);
    }

    @Override
    public long duration(long task) {
        return timer().duration(task);
    }

    @Override
    public long duration() {
        return timer().duration();
    }

    @Override
    public int activeTasks() {
        return timer().activeTasks();
    }
}
