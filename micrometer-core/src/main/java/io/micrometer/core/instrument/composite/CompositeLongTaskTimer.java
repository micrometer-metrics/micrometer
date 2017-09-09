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
package io.micrometer.core.instrument.composite;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.noop.NoopLongTaskTimer;
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CompositeLongTaskTimer extends AbstractMeter implements LongTaskTimer, CompositeMeter {
    private final Map<MeterRegistry, LongTaskTimer> timers = Collections.synchronizedMap(new LinkedHashMap<>());

    CompositeLongTaskTimer(Meter.Id id) {
        super(id);
    }

    @Override
    public long start() {
        synchronized (timers) {
            return timers.values().stream()
                .map(LongTaskTimer::start)
                .reduce((t1, t2) -> t2)
                .orElse(NoopLongTaskTimer.INSTANCE.start());
        }
    }

    @Override
    public long stop(long task) {
        synchronized (timers) {
            return timers.values().stream()
                .map(ltt -> ltt.stop(task))
                .reduce((t1, t2) -> t2 == -1 ? t1 : t2)
                .orElse(NoopLongTaskTimer.INSTANCE.stop(task));
        }
    }

    @Override
    public double duration(long task, TimeUnit unit) {
        synchronized (timers) {
            return timers.values().stream()
                .map(ltt -> ltt.duration(task, unit))
                .reduce((t1, t2) -> t2 == -1 ? t1 : t2)
                .orElse(NoopLongTaskTimer.INSTANCE.duration(task, unit));
        }
    }

    @Override
    public double duration(TimeUnit unit) {
        synchronized (timers) {
            return timers.values().stream()
                .map(ltt -> ltt.duration(unit))
                .reduce((t1, t2) -> t2)
                .orElse(NoopLongTaskTimer.INSTANCE.duration(unit));
        }
    }

    @Override
    public int activeTasks() {
        synchronized (timers) {
            return timers.values().stream()
                .map(LongTaskTimer::activeTasks)
                .reduce((t1, t2) -> t2)
                .orElse(NoopLongTaskTimer.INSTANCE.activeTasks());
        }
    }

    @Override
    public void add(MeterRegistry registry) {
        synchronized (timers) {
            timers.put(registry, registry.more().longTaskTimer(getId()));
        }
    }

    @Override
    public void remove(MeterRegistry registry) {
        synchronized (timers) {
            timers.remove(registry);
        }
    }
}
