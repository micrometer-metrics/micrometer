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
package io.micrometer.core.instrument;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.lang.Nullable;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A long task timer is used to track the total duration of all in-flight long-running tasks and the number of
 * such tasks.
 *
 * @author Jon Schneider
 */
public interface LongTaskTimer extends Meter {
    static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Create a timer builder from a {@link Timed} annotation.
     *
     * @param timed The annotation instance to base a new timer on.
     * @return This builder.
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

    /**
     * Executes the callable {@code f} and records the time taken.
     *
     * @param f   Function to execute and measure the execution time.
     * @param <T> The return type of the {@link Callable}.
     * @return The return value of {@code f}.
     * @throws Exception Any exception bubbling up from the callable.
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
     * Executes the callable {@code f} and records the time taken.
     *
     * @param f   Function to execute and measure the execution time.
     * @param <T> The return type of the {@link Supplier}.
     * @return The return value of {@code f}.
     */
    default <T> T record(Supplier<T> f) {
        Sample sample = start();
        try {
            return f.get();
        } finally {
            sample.stop();
        }
    }

    /**
     * Executes the runnable {@code f} and records the time taken.
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
     * Executes the runnable {@code f} and records the time taken.
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
     * The current duration for an active task.
     *
     * @param task Id for the task to stop. This should be the value returned from {@link #start()}.
     * @param unit The time unit to scale the duration to.
     * @return Duration for the task. A -1 value will be returned for an unknown task.
     */
    double duration(long task, TimeUnit unit);

    /**
     * @param unit The time unit to scale the duration to.
     * @return The cumulative duration of all current tasks.
     */
    double duration(TimeUnit unit);

    /**
     * @return The current number of tasks being executed.
     */
    int activeTasks();

    @Override
    default Iterable<Measurement> measure() {
        return Arrays.asList(
                new Measurement(() -> (double) activeTasks(), Statistic.ACTIVE_TASKS),
                new Measurement(() -> duration(TimeUnit.NANOSECONDS), Statistic.DURATION)
        );
    }

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
         * @return The duration, in nanoseconds, of this sample that was stopped
         */
        public long stop() {
            return timer.stop(task);
        }

        /**
         * @param unit time unit to which the return value will be scaled
         * @return duration of this sample
         */
        public double duration(TimeUnit unit) {
            return timer.duration(task, unit);
        }
    }

    /**
     * Fluent builder for long task timers.
     */
    class Builder {
        private final String name;
        private Tags tags = Tags.empty();

        @Nullable
        private String description;

        private Builder(String name) {
            this.name = name;
        }

        /**
         * @param tags Must be an even number of arguments representing key/value pairs of tags.
         * @return The long task timer builder with added tags.
         */
        public Builder tags(String... tags) {
            return tags(Tags.of(tags));
        }

        /**
         * @param tags Tags to add to the eventual long task timer.
         * @return The long task timer builder with added tags.
         */
        public Builder tags(Iterable<Tag> tags) {
            this.tags = this.tags.and(tags);
            return this;
        }

        /**
         * @param key   The tag key.
         * @param value The tag value.
         * @return The long task timer builder with a single added tag.
         */
        public Builder tag(String key, String value) {
            this.tags = tags.and(key, value);
            return this;
        }

        /**
         * @param description Description text of the eventual long task timer.
         * @return The long task timer builder with added description.
         */
        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        /**
         * Add the long task timer to a single registry, or return an existing long task timer in that registry. The returned
         * long task timer will be unique for each registry, but each registry is guaranteed to only create one long task timer
         * for the same combination of name and tags.
         *
         * @param registry A registry to add the long task timer to, if it doesn't already exist.
         * @return A new or existing long task timer.
         */
        public LongTaskTimer register(MeterRegistry registry) {
            return registry.more().longTaskTimer(new Meter.Id(name, tags, null, description, Type.LONG_TASK_TIMER));
        }
    }
}
