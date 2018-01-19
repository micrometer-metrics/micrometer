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
package io.micrometer.core.instrument;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.lang.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface LongTaskTimer extends Meter {
    class Sample {
        private final LongTaskTimer timer;
        private final long task;

        public Sample(LongTaskTimer timer, long task) {
            this.timer = timer;
            this.task = task;
        }

        /**
         * Records the duration of the operation
         *
         * @return The duration that was stop in nanoseconds
         */
        public long stop() {
            return timer.stop(task);
        }

        public double duration(TimeUnit unit) {
            return timer.duration(task, unit);
        }
    }

    /**
     * Executes the callable `f` and records the time taken.
     *
     * @param f Function to execute and measure the execution time.
     * @return The return value of `f`.
     */
    default <T> T recordCallable(Callable<T> f) throws Exception {
        Sample sample = start();
        try {
            return f.call();
        } finally {
            sample.stop();
        }
    }

    /**
     * Executes the callable `f` and records the time taken.
     *
     * @param f Function to execute and measure the execution time.
     * @return The return value of `f`.
     */
    default <T> T record(Supplier<T> f) throws Exception {
        Sample sample = start();
        try {
            return f.get();
        } finally {
            sample.stop();
        }
    }

    /**
     * Executes the runnable `f` and records the time taken.
     *
     * @param f Function to execute and measure the execution time with a reference to the
     *          timer id useful for looking up current duration.
     */
    default void record(Consumer<Sample> f) {
        Sample sample = start();
        try {
            f.accept(sample);
        } finally {
            sample.stop();
        }
    }

    /**
     * Executes the runnable `f` and records the time taken.
     *
     * @param f Function to execute and measure the execution time.
     */
    default void record(Runnable f) {
        Sample sample = start();
        try {
            f.run();
        } finally {
            sample.stop();
        }
    }

    /**
     * Start keeping time for a task.
     *
     * @return A task id that can be used to look up how long the task has been running.
     */
    Sample start();

    /**
     * Mark a given task as completed.
     *
     * @param task Id for the task to stop. This should be the value returned from {@link #start()}.
     * @return Duration for the task in nanoseconds. A -1 value will be returned for an unknown task.
     */
    long stop(long task);

    /**
     * Returns the current duration for an active task.
     *
     * @param task Id for the task to stop. This should be the value returned from {@link #start()}.
     * @param unit The time unit to scale the returned value to.
     * @return Duration for the task in nanoseconds. A -1 value will be returned for an unknown task.
     */
    double duration(long task, TimeUnit unit);

    /**
     * Returns the cumulative duration of all current tasks in nanoseconds.
     */
    double duration(TimeUnit unit);

    /**
     * Returns the current number of tasks being executed.
     */
    int activeTasks();

    @Override
    default Iterable<Measurement> measure() {
        return Arrays.asList(
            new Measurement(() -> (double) activeTasks(), Statistic.ActiveTasks),
            new Measurement(() -> duration(TimeUnit.NANOSECONDS), Statistic.Duration)
        );
    }

    @Override
    default Type type() {
        return Type.LongTaskTimer;
    }

    static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Create a timer builder from a {@link Timed} annotation.
     *
     * @param timed The annotation instance to base a new timer on.
     */
    static Builder builder(Timed timed) {
        if (!timed.longTask()) {
            throw new IllegalArgumentException("Cannot build a long task timer from a @Timed annotation that is not marked as a long task");
        }

        if (timed.value().isEmpty()) {
            throw new IllegalArgumentException("Long tasks instrumented with @Timed require the value attribute to be non-empty");
        }

        return new Builder(timed.value())
            .tags(timed.extraTags())
            .description(timed.description().isEmpty() ? null : timed.description());
    }

    class Builder {
        private final String name;
        private final List<Tag> tags = new ArrayList<>();
        @Nullable private String description;

        private Builder(String name) {
            this.name = name;
        }

        /**
         * @param tags Must be an even number of arguments representing key/value pairs of tags.
         */
        public Builder tags(String... tags) {
            return tags(Tags.zip(tags));
        }

        public Builder tags(Iterable<Tag> tags) {
            tags.forEach(this.tags::add);
            return this;
        }

        public Builder tag(String key, String value) {
            tags.add(Tag.of(key, value));
            return this;
        }

        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        public LongTaskTimer register(MeterRegistry registry) {
            return registry.more().longTaskTimer(new Meter.Id(name, tags, null, description, Type.LongTaskTimer));
        }
    }
}
