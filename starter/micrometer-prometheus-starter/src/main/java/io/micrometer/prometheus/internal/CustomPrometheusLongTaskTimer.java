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
package io.micrometer.prometheus.internal;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.util.TimeUtils;
import io.prometheus.client.Collector;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

public class CustomPrometheusLongTaskTimer extends Collector {
    private final Clock clock;
    private final String name;
    private final String activeTasksName;
    private final String durationName;
    private final String description;
    private final List<String> tagKeys;
    private final Collection<Child> children = new ConcurrentLinkedQueue<>();

    public CustomPrometheusLongTaskTimer(Meter.Id id, Clock clock) {
        this.name = id.getConventionName();
        this.description = id.getDescription();
        this.clock = clock;
        this.tagKeys = id.getConventionTags().stream().map(Tag::getKey).collect(toList());

        this.activeTasksName = name + "_active_count";
        this.durationName = name + "_sum";
    }

    public Child child(Iterable<Tag> tags) {
        Child child = new Child(tags);
        children.add(child);
        return child;
    }

    public class Child implements CustomCollectorChild {
        private final List<String> tagValues;
        private final ConcurrentMap<Long, Long> tasks = new ConcurrentHashMap<>();
        private final AtomicLong nextTask = new AtomicLong(0L);

        Child(Iterable<Tag> tags) {
            this.tagValues = stream(tags.spliterator(), false).map(Tag::getValue).collect(toList());
        }

        @Override
        public Stream<MetricFamilySamples.Sample> collect() {
            Stream.Builder<MetricFamilySamples.Sample> samples = Stream.builder();
            samples.add(new MetricFamilySamples.Sample(activeTasksName, tagKeys, tagValues, activeTasks()));
            samples.add(new MetricFamilySamples.Sample(durationName, tagKeys, tagValues, duration(TimeUnit.SECONDS)));
            return samples.build();
        }

        public long start() {
            long task = nextTask.getAndIncrement();
            tasks.put(task, clock.monotonicTime());
            return task;
        }

        public long stop(long task) {
            Long startTime = tasks.get(task);
            if (startTime != null) {
                tasks.remove(task);
                return clock.monotonicTime() - startTime;
            } else {
                return -1L;
            }
        }

        public double duration(long task, TimeUnit unit) {
            Long startTime = tasks.get(task);
            return (startTime != null) ? TimeUtils.nanosToUnit(clock.monotonicTime() - startTime, unit) : -1L;
        }

        public double duration(TimeUnit unit) {
            long now = clock.monotonicTime();
            long sum = 0L;
            for (long startTime : tasks.values()) {
                sum += now - startTime;
            }
            return TimeUtils.nanosToUnit(sum, unit);
        }

        public int activeTasks() {
            return tasks.size();
        }
    }

    @Override
    public List<MetricFamilySamples> collect() {
        return Collections.singletonList(new MetricFamilySamples(name, Type.UNTYPED, description == null ? " " : description, children.stream()
                .flatMap(Child::collect).collect(toList())));
    }
}
