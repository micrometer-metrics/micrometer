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
package io.micrometer.core.instrument.composite;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.noop.NoopLongTaskTimer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

class CompositeLongTaskTimer extends AbstractCompositeMeter<LongTaskTimer> implements LongTaskTimer {
    private final AtomicLong nextTask = new AtomicLong(0L);
    private final ConcurrentMap<Long, Collection<Sample>> timings = new ConcurrentHashMap<>();

    CompositeLongTaskTimer(Meter.Id id) {
        super(id);
    }

    @Override
    public Sample start() {
        long task = nextTask.getAndIncrement();

        Collection<Sample> samples = new ArrayList<>();
        forEachChild(ltt -> samples.add(ltt.start()));
        timings.put(task, samples);

        return new Sample(this, task);
    }

    @Override
    public long stop(long task) {
        Collection<Sample> childMappings = timings.remove(task);
        long last = 0;
        if (childMappings != null) {
            for (Sample sample : childMappings) {
                last = sample.stop();
            }
        }
        return last;
    }

    @Override
    public double duration(long task, TimeUnit unit) {
        Collection<Sample> childSamples = timings.get(task);
        if (childSamples != null) {
            return childSamples.stream().findFirst().map(c -> c.duration(unit)).orElse(-1.0);
        }
        return -1.0;
    }

    @Override
    public double duration(TimeUnit unit) {
        return firstChild().duration(unit);
    }

    @Override
    public int activeTasks() {
        return firstChild().activeTasks();
    }

    @Override
    LongTaskTimer newNoopMeter() {
        return new NoopLongTaskTimer(getId());
    }

    @Override
    LongTaskTimer registerNewMeter(MeterRegistry registry) {
        return LongTaskTimer.builder(getId().getName())
                .tags(getId().getTags())
                .description(getId().getDescription())
                .register(registry);
    }
}
