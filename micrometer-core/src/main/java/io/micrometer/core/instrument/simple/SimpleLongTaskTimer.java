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
package io.micrometer.core.instrument.simple;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.util.MeterId;
import io.micrometer.core.instrument.util.Meters;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class SimpleLongTaskTimer extends AbstractSimpleMeter implements LongTaskTimer {
    private static final Tag TYPE_TAG = SimpleUtils.typeTag(Type.LongTaskTimer);
    private static final Tag STAT_ACTIVE_TASKS_TAG = Tag.of("statistic", "activeTasks");
    private static final Tag STAT_DURATION_TAG = Tag.of("statistic", "duration");

    private final ConcurrentMap<Long, Long> tasks = new ConcurrentHashMap<>();
    private final AtomicLong nextTask = new AtomicLong(0L);
    private final Clock clock;

    private final MeterId activeTasksId;
    private final MeterId durationId;

    public SimpleLongTaskTimer(MeterId id, Clock clock) {
        super(id);
        this.clock = clock;
        this.activeTasksId = id.withTags(TYPE_TAG, STAT_ACTIVE_TASKS_TAG);
        this.durationId = id.withTags(TYPE_TAG, STAT_DURATION_TAG);
    }

    @Override
    public long start() {
        long task = nextTask.getAndIncrement();
        tasks.put(task, clock.monotonicTime());
        return task;
    }

    @Override
    public long stop(long task) {
        Long startTime = tasks.get(task);
        if (startTime != null) {
            tasks.remove(task);
            return clock.monotonicTime() - startTime;
        } else {
            return -1L;
        }
    }

    @Override
    public long duration(long task) {
        Long startTime = tasks.get(task);
        return (startTime != null) ? (clock.monotonicTime() - startTime) : -1L;
    }

    @Override
    public long duration() {
        long now = clock.monotonicTime();
        long sum = 0L;
        for (long startTime : tasks.values()) {
            sum += now - startTime;
        }
        return sum;
    }

    @Override
    public int activeTasks() {
        return tasks.size();
    }

    @Override
    public List<Measurement> measure() {
        return Arrays.asList(
                activeTasksId.measurement(activeTasks()),
                durationId.measurement(duration()));
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return Meters.equals(this, o);
    }

    @Override
    public int hashCode() {
        return Meters.hashCode(this);
    }
}
