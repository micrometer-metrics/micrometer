/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSupport;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

/**
 * A long task timer is used to track the total duration of all in-flight long-running
 * tasks and the number of such tasks.
 *
 * @author Jon Schneider
 * @author Jonatan Ivanov
 */
public interface LongTaskTimer extends Meter, HistogramSupport {

    static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Create a timer builder from a {@link Timed} annotation.
     * @param timed The annotation instance to base a new timer on.
     * @return This builder.
     */
    static Builder builder(Timed timed) {
        if (!timed.longTask()) {
            throw new IllegalArgumentException(
                    "Cannot build a long task timer from a @Timed annotation that is not marked as a long task");
        }

        if (timed.value().isEmpty()) {
            throw new IllegalArgumentException(
                    "Long tasks instrumented with @Timed require the value attribute to be non-empty");
        }

        return new Builder(timed.value()).tags(timed.extraTags())
            .publishPercentileHistogram(timed.histogram())
            .description(timed.description().isEmpty() ? null : timed.description());
    }

    /**
     * Executes the callable {@code f} and records the time taken.
     * @param f Function to execute and measure the execution time.
     * @param <T> The return type of the {@link Callable}.
     * @return The return value of {@code f}.
     * @throws Exception Any exception bubbling up from the callable.
     */
    @Nullable
    default <T> T recordCallable(Callable<T> f) throws Exception {
        Sample sample = start();
        try {
            return f.call();
        }
        finally {
            sample.stop();
        }
    }

    /**
     * Executes the supplier {@code f} and records the time taken.
     * @param f Function to execute and measure the execution time.
     * @param <T> The return type of the {@link Supplier}.
     * @return The return value of {@code f}.
     */
    @Nullable
    default <T> T record(Supplier<T> f) {
        Sample sample = start();
        try {
            return f.get();
        }
        finally {
            sample.stop();
        }
    }

    /**
     * Executes the supplier {@code f} and records the time taken.
     * @param f Function to execute and measure the execution time.
     * @return The return value of {@code f}.
     * @since 1.10.0
     */
    default boolean record(BooleanSupplier f) {
        Sample sample = start();
        try {
            return f.getAsBoolean();
        }
        finally {
            sample.stop();
        }
    }

    /**
     * Executes the supplier {@code f} and records the time taken.
     * @param f Function to execute and measure the execution time.
     * @return The return value of {@code f}.
     * @since 1.10.0
     */
    default int record(IntSupplier f) {
        Sample sample = start();
        try {
            return f.getAsInt();
        }
        finally {
            sample.stop();
        }
    }

    /**
     * Executes the supplier {@code f} and records the time taken.
     * @param f Function to execute and measure the execution time.
     * @return The return value of {@code f}.
     * @since 1.10.0
     */
    default long record(LongSupplier f) {
        Sample sample = start();
        try {
            return f.getAsLong();
        }
        finally {
            sample.stop();
        }
    }

    /**
     * Executes the supplier {@code f} and records the time taken.
     * @param f Function to execute and measure the execution time.
     * @return The return value of {@code f}.
     * @since 1.10.0
     */
    default double record(DoubleSupplier f) {
        Sample sample = start();
        try {
            return f.getAsDouble();
        }
        finally {
            sample.stop();
        }
    }

    /**
     * Executes the runnable {@code f} and records the time taken.
     * @param f Function to execute and measure the execution time with a reference to the
     * timer id useful for looking up current duration.
     */
    default void record(Consumer<Sample> f) {
        Sample sample = start();
        try {
            f.accept(sample);
        }
        finally {
            sample.stop();
        }
    }

    /**
     * Executes the runnable {@code f} and records the time taken.
     * @param f Function to execute and measure the execution time.
     */
    default void record(Runnable f) {
        Sample sample = start();
        try {
            f.run();
        }
        finally {
            sample.stop();
        }
    }

    /**
     * Start keeping time for a task.
     * @return A task id that can be used to look up how long the task has been running.
     */
    Sample start();

    /**
     * @param unit The time unit to scale the duration to.
     * @return The cumulative duration of all current tasks.
     */
    double duration(TimeUnit unit);

    /**
     * @return The current number of tasks being executed.
     */
    int activeTasks();

    /**
     * @param unit The base unit of time to scale the mean to.
     * @return The distribution average for all recorded events.
     * @since 1.5.1
     */
    default double mean(TimeUnit unit) {
        int activeTasks = activeTasks();
        return activeTasks == 0 ? 0 : duration(unit) / activeTasks;
    }

    /**
     * The amount of time the longest running task has been running
     * @param unit The time unit to scale the max to.
     * @return The maximum active task duration.
     * @since 1.5.0
     */
    double max(TimeUnit unit);

    /**
     * @return The base time unit of the long task timer to which all published metrics
     * will be scaled
     * @since 1.5.0
     */
    TimeUnit baseTimeUnit();

    /**
     * Mark a given task as completed.
     * @param task Id for the task to stop. This should be the value returned from
     * {@link #start()}.
     * @return Duration for the task in nanoseconds. A -1 value will be returned for an
     * unknown task.
     * @deprecated Use {@link Sample#stop()}. As of 1.5.0, this always returns -1 as tasks
     * no longer have IDs.
     */
    @SuppressWarnings("unused")
    @Deprecated
    default long stop(long task) {
        return -1;
    }

    /**
     * The current duration for an active task.
     * @param task Id for the task to stop. This should be the value returned from
     * {@link #start()}.
     * @param unit The time unit to scale the duration to.
     * @return Duration for the task. A -1 value will be returned for an unknown task.
     * @deprecated Use {@link Sample#duration(TimeUnit)}. As of 1.5.0, this always returns
     * -1 as tasks no longer have IDs.
     */
    @SuppressWarnings("unused")
    @Deprecated
    default double duration(long task, TimeUnit unit) {
        return -1;
    }

    @Override
    default Iterable<Measurement> measure() {
        return Arrays.asList(new Measurement(() -> (double) activeTasks(), Statistic.ACTIVE_TASKS),
                new Measurement(() -> duration(baseTimeUnit()), Statistic.DURATION));
    }

    abstract class Sample {

        /**
         * Records the duration of the operation
         * @return The duration, in nanoseconds, of this sample that was stopped
         */
        public abstract long stop();

        /**
         * @param unit time unit to which the return value will be scaled
         * @return duration of this sample
         */
        public abstract double duration(TimeUnit unit);

    }

    /**
     * Fluent builder for long task timers.
     */
    class Builder {

        private final String name;

        private Tags tags = Tags.empty();

        private final DistributionStatisticConfig.Builder distributionConfigBuilder = new DistributionStatisticConfig.Builder();

        @Nullable
        private String description;

        private Builder(String name) {
            this.name = name;
            minimumExpectedValue(Duration.ofMinutes(2));
            maximumExpectedValue(Duration.ofHours(2));
        }

        /**
         * @param tags Must be an even number of arguments representing key/value pairs of
         * tags.
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
         * @param key The tag key.
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
         * Publish at a minimum a histogram containing your defined service level
         * objective (SLO) boundaries. When used in conjunction with
         * {@link Builder#publishPercentileHistogram()}, the boundaries defined here are
         * included alongside other buckets used to generate aggregable percentile
         * approximations.
         * @param slos Publish SLO boundaries in the set of histogram buckets shipped to
         * the monitoring system.
         * @return This builder.
         * @since 1.5.0
         */
        public Builder serviceLevelObjectives(@Nullable Duration... slos) {
            if (slos != null) {
                this.distributionConfigBuilder
                    .serviceLevelObjectives(Arrays.stream(slos).mapToDouble(Duration::toNanos).toArray());
            }
            return this;
        }

        /**
         * Sets the minimum value that this timer is expected to observe. Sets a lower
         * bound on histogram buckets that are shipped to monitoring systems that support
         * aggregable percentile approximations.
         * @param min The minimum value that this timer is expected to observe.
         * @return This builder.
         * @since 1.5.0
         */
        public Builder minimumExpectedValue(@Nullable Duration min) {
            if (min != null)
                this.distributionConfigBuilder.minimumExpectedValue((double) min.toNanos());
            return this;
        }

        /**
         * Sets the maximum value that this timer is expected to observe. Sets an upper
         * bound on histogram buckets that are shipped to monitoring systems that support
         * aggregable percentile approximations.
         * @param max The maximum value that this timer is expected to observe.
         * @return This builder.
         * @since 1.5.0
         */
        public Builder maximumExpectedValue(@Nullable Duration max) {
            if (max != null)
                this.distributionConfigBuilder.maximumExpectedValue((double) max.toNanos());
            return this;
        }

        /**
         * Statistics emanating from a timer like max, percentiles, and histogram counts
         * decay over time to give greater weight to recent samples (exception: histogram
         * counts are cumulative for those systems that expect cumulative histogram
         * buckets). Samples are accumulated to such statistics in ring buffers which
         * rotate after this expiry, with a buffer length of
         * {@link #distributionStatisticBufferLength(Integer)}.
         * @param expiry The amount of time samples are accumulated to a histogram before
         * it is reset and rotated.
         * @return This builder.
         * @since 1.5.0
         */
        public Builder distributionStatisticExpiry(@Nullable Duration expiry) {
            this.distributionConfigBuilder.expiry(expiry);
            return this;
        }

        /**
         * Statistics emanating from a timer like max, percentiles, and histogram counts
         * decay over time to give greater weight to recent samples (exception: histogram
         * counts are cumulative for those systems that expect cumulative histogram
         * buckets). Samples are accumulated to such statistics in ring buffers which
         * rotate after {@link #distributionStatisticExpiry(Duration)}, with this buffer
         * length.
         * @param bufferLength The number of histograms to keep in the ring buffer.
         * @return This builder.
         * @since 1.5.0
         */
        public Builder distributionStatisticBufferLength(@Nullable Integer bufferLength) {
            this.distributionConfigBuilder.bufferLength(bufferLength);
            return this;
        }

        /**
         * Produces an additional time series for each requested percentile. This
         * percentile is computed locally, and so can't be aggregated with percentiles
         * computed across other dimensions (e.g. in a different instance). Use
         * {@link #publishPercentileHistogram()} to publish a histogram that can be used
         * to generate aggregable percentile approximations.
         * @param percentiles Percentiles to compute and publish. The 95th percentile
         * should be expressed as {@code 0.95}.
         * @return This builder.
         * @since 1.5.0
         */
        public Builder publishPercentiles(@Nullable double... percentiles) {
            this.distributionConfigBuilder.percentiles(percentiles);
            return this;
        }

        /**
         * Determines the number of digits of precision to maintain on the dynamic range
         * histogram used to compute percentile approximations. The higher the degrees of
         * precision, the more accurate the approximation is at the cost of more memory.
         * @param digitsOfPrecision The digits of precision to maintain for percentile
         * approximations.
         * @return This builder.
         * @since 1.5.0
         */
        public Builder percentilePrecision(@Nullable Integer digitsOfPrecision) {
            this.distributionConfigBuilder.percentilePrecision(digitsOfPrecision);
            return this;
        }

        /**
         * Adds histogram buckets used to generate aggregable percentile approximations in
         * monitoring systems that have query facilities to do so (e.g. Prometheus'
         * {@code histogram_quantile}, Atlas' {@code :percentiles}).
         * @return This builder.
         * @since 1.5.0
         */
        public Builder publishPercentileHistogram() {
            return publishPercentileHistogram(true);
        }

        /**
         * Adds histogram buckets used to generate aggregable percentile approximations in
         * monitoring systems that have query facilities to do so (e.g. Prometheus'
         * {@code histogram_quantile}, Atlas' {@code :percentiles}).
         * @param enabled Determines whether percentile histograms should be published.
         * @return This builder.
         * @since 1.5.0
         */
        public Builder publishPercentileHistogram(@Nullable Boolean enabled) {
            this.distributionConfigBuilder.percentilesHistogram(enabled);
            return this;
        }

        /**
         * Convenience method to create meters from the builder that only differ in tags.
         * This method can be used for dynamic tagging by creating the builder once and
         * applying the dynamically changing tags using the returned
         * {@link MeterProvider}.
         * @param registry A registry to add the meter to, if it doesn't already exist.
         * @return A {@link MeterProvider} that returns a meter based on the provided
         * tags.
         * @since 1.12.0
         */
        public MeterProvider<LongTaskTimer> withRegistry(MeterRegistry registry) {
            return extraTags -> register(registry, tags.and(extraTags));
        }

        /**
         * Add the long task timer to a single registry, or return an existing long task
         * timer in that registry. The returned long task timer will be unique for each
         * registry, but each registry is guaranteed to only create one long task timer
         * for the same combination of name and tags.
         * @param registry A registry to add the long task timer to, if it doesn't already
         * exist.
         * @return A new or existing long task timer.
         */
        public LongTaskTimer register(MeterRegistry registry) {
            return register(registry, tags);
        }

        private LongTaskTimer register(MeterRegistry registry, Tags tags) {
            return registry.more()
                .longTaskTimer(new Meter.Id(name, tags, null, description, Type.LONG_TASK_TIMER),
                        distributionConfigBuilder.build());
        }

    }

}
