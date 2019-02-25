/**
 * Copyright 2019 Pivotal Software, Inc.
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
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSupport;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.lang.Nullable;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Timer intended to track of a large number of short running events. Example would be something like
 * an HTTP request. Though "short running" is a bit subjective the assumption is that it should be
 * under a minute.
 *
 * @author Jon Schneider
 * @author Oleksii Bondar
 */
public interface Timer extends Meter, HistogramSupport {
    /**
     * Start a timing sample.
     * @return A timing sample with start time recorded.
     * @since 1.1.0
     */
    static Sample start() {
        return start(Clock.SYSTEM);
    }

    /**
     * Start a timing sample.
     * @param registry a meter registry to be used
     * @return A timing sample with start time recorded.
     */
    static Sample start(MeterRegistry registry) {
        return start(registry.config().clock());
    }

    /**
     * Start a timing sample.
     * @param clock a clock to be used
     * @return A timing sample with start time recorded.
     */
    static Sample start(Clock clock) {
        return new Sample(clock);
    }

    static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Create a timer builder from a {@link Timed} annotation.
     *
     * @param timed       The annotation instance to base a new timer on.
     * @param defaultName A default name to use in the event that the value attribute is empty.
     * @return This builder.
     */
    static Builder builder(Timed timed, String defaultName) {
        if (timed.longTask() && timed.value().isEmpty()) {
            // the user MUST name long task timers, we don't lump them in with regular
            // timers with the same name
            throw new IllegalArgumentException("Long tasks instrumented with @Timed require the value attribute to be non-empty");
        }

        return new Builder(timed.value().isEmpty() ? defaultName : timed.value())
                .tags(timed.extraTags())
                .description(timed.description().isEmpty() ? null : timed.description())
                .publishPercentileHistogram(timed.histogram())
                .publishPercentiles(timed.percentiles().length > 0 ? timed.percentiles() : null);
    }

    /**
     * Updates the statistics kept by the timer with the specified amount.
     *
     * @param amount Duration of a single event being measured by this timer. If the amount is less than 0
     *               the value will be dropped.
     * @param unit   Time unit for the amount being recorded.
     */
    void record(long amount, TimeUnit unit);

    /**
     * Updates the statistics kept by the timer with the specified amount.
     *
     * @param duration Duration of a single event being measured by this timer.
     */
    default void record(Duration duration) {
        record(duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    /**
     * Executes the Supplier {@code f} and records the time taken.
     *
     * @param f   Function to execute and measure the execution time.
     * @param <T> The return type of the {@link Supplier}.
     * @return The return value of {@code f}.
     */
    <T> T record(Supplier<T> f);

    /**
     * Executes the callable {@code f} and records the time taken.
     *
     * @param f   Function to execute and measure the execution time.
     * @param <T> The return type of the {@link Callable}.
     * @return The return value of {@code f}.
     * @throws Exception Any exception bubbling up from the callable.
     */
    <T> T recordCallable(Callable<T> f) throws Exception;

    /**
     * Executes the runnable {@code f} and records the time taken.
     *
     * @param f Function to execute and measure the execution time.
     */
    void record(Runnable f);

    /**
     * Wrap a {@link Runnable} so that it is timed when invoked.
     *
     * @param f The Runnable to time when it is invoked.
     * @return The wrapped Runnable.
     */
    default Runnable wrap(Runnable f) {
        return () -> record(f);
    }

    /**
     * Wrap a {@link Callable} so that it is timed when invoked.
     *
     * @param f   The Callable to time when it is invoked.
     * @param <T> The return type of the callable.
     * @return The wrapped callable.
     */
    default <T> Callable<T> wrap(Callable<T> f) {
        return () -> recordCallable(f);
    }
    
    /**
     * Wrap a {@link Supplier} so that it is timed when invoked.
     *
     * @param f The {@code Supplier} to time when it is invoked.
     * @param <T> The return type of the {@code Supplier} result.
     * @return The wrapped supplier.
     * @since 1.2.0
     */
    default <T> Supplier<T> wrap(Supplier<T> f) {
        return () -> record(f);
    }

    /**
     * @return The number of times that stop has been called on this timer.
     */
    long count();

    /**
     * @param unit The base unit of time to scale the total to.
     * @return The total time of recorded events.
     */
    double totalTime(TimeUnit unit);

    /**
     * @param unit The base unit of time to scale the mean to.
     * @return The distribution average for all recorded events.
     */
    default double mean(TimeUnit unit) {
        return count() == 0 ? 0 : totalTime(unit) / count();
    }

    /**
     * @param unit The base unit of time to scale the max to.
     * @return The maximum time of a single event.
     */
    double max(TimeUnit unit);

    @Override
    default Iterable<Measurement> measure() {
        return Arrays.asList(
                new Measurement(() -> (double) count(), Statistic.COUNT),
                new Measurement(() -> totalTime(baseTimeUnit()), Statistic.TOTAL_TIME),
                new Measurement(() -> max(baseTimeUnit()), Statistic.MAX)
        );
    }

    /**
     * Provides cumulative histogram counts.
     *
     * @param valueNanos The histogram bucket to retrieve a count for.
     * @return The count of all events less than or equal to the bucket. If valueNanos does not
     * match a preconfigured bucket boundary, returns NaN.
     * @deprecated Use {@link #takeSnapshot()} to retrieve bucket counts.
     */
    @Deprecated
    default double histogramCountAtValue(long valueNanos) {
        for (CountAtBucket countAtBucket : takeSnapshot().histogramCounts()) {
            if ((long) countAtBucket.bucket(TimeUnit.NANOSECONDS) == valueNanos) {
                return countAtBucket.count();
            }
        }
        return Double.NaN;
    }

    /**
     * @param percentile A percentile in the domain [0, 1]. For example, 0.5 represents the 50th percentile of the
     *                   distribution.
     * @param unit       The base unit of time to scale the percentile value to.
     * @return The latency at a specific percentile. This value is non-aggregable across dimensions. Returns NaN if
     * percentile is not a preconfigured percentile that Micrometer is tracking.
     * @deprecated Use {@link #takeSnapshot()} to retrieve bucket counts.
     */
    @Deprecated
    default double percentile(double percentile, TimeUnit unit) {
        for (ValueAtPercentile valueAtPercentile : takeSnapshot().percentileValues()) {
            if (valueAtPercentile.percentile() == percentile) {
                return valueAtPercentile.value(unit);
            }
        }
        return Double.NaN;
    }

    /**
     * @return The base time unit of the timer to which all published metrics will be scaled
     */
    TimeUnit baseTimeUnit();

    /**
     * Maintains state on the clock's start position for a latency sample. Complete the timing
     * by calling {@link Sample#stop(Timer)}. Note how the {@link Timer} isn't provided until the
     * sample is stopped, allowing you to determine the timer's tags at the last minute.
     */
    class Sample {
        private final long startTime;
        private final Clock clock;

        Sample(Clock clock) {
            this.clock = clock;
            this.startTime = clock.monotonicTime();
        }

        /**
         * Records the duration of the operation
         *
         * @param timer The timer to record the sample to.
         * @return The total duration of the sample in nanoseconds
         */
        public long stop(Timer timer) {
            long durationNs = clock.monotonicTime() - startTime;
            timer.record(durationNs, TimeUnit.NANOSECONDS);
            return durationNs;
        }
    }

    /**
     * Fluent builder for timers.
     */
    class Builder {
        private final String name;
        private Tags tags = Tags.empty();
        private final DistributionStatisticConfig.Builder distributionConfigBuilder;

        @Nullable
        private String description;

        @Nullable
        private PauseDetector pauseDetector;

        private Builder(String name) {
            this.name = name;
            this.distributionConfigBuilder = new DistributionStatisticConfig.Builder();
            minimumExpectedValue(Duration.ofMillis(1));
            maximumExpectedValue(Duration.ofSeconds(30));
        }

        /**
         * @param tags Must be an even number of arguments representing key/value pairs of tags.
         * @return This builder.
         */
        public Builder tags(String... tags) {
            return tags(Tags.of(tags));
        }

        /**
         * @param tags Tags to add to the eventual timer.
         * @return The timer builder with added tags.
         */
        public Builder tags(Iterable<Tag> tags) {
            this.tags = this.tags.and(tags);
            return this;
        }

        /**
         * @param key   The tag key.
         * @param value The tag value.
         * @return The timer builder with a single added tag.
         */
        public Builder tag(String key, String value) {
            this.tags = tags.and(key, value);
            return this;
        }

        /**
         * Produces an additional time series for each requested percentile. This percentile
         * is computed locally, and so can't be aggregated with percentiles computed across other
         * dimensions (e.g. in a different instance). Use {@link #publishPercentileHistogram()}
         * to publish a histogram that can be used to generate aggregable percentile approximations.
         *
         * @param percentiles Percentiles to compute and publish. The 95th percentile should be expressed as {@code 0.95}.
         * @return This builder.
         */
        public Builder publishPercentiles(@Nullable double... percentiles) {
            this.distributionConfigBuilder.percentiles(percentiles);
            return this;
        }

        /**
         * Determines the number of digits of precision to maintain on the dynamic range histogram used to compute
         * percentile approximations. The higher the degrees of precision, the more accurate the approximation is at the
         * cost of more memory.
         *
         * @param digitsOfPrecision The digits of precision to maintain for percentile approximations.
         * @return This builder.
         */
        public Builder percentilePrecision(@Nullable Integer digitsOfPrecision) {
            this.distributionConfigBuilder.percentilePrecision(digitsOfPrecision);
            return this;
        }

        /**
         * Adds histogram buckets used to generate aggregable percentile approximations in monitoring
         * systems that have query facilities to do so (e.g. Prometheus' {@code histogram_quantile},
         * Atlas' {@code :percentiles}).
         *
         * @return This builder.
         */
        public Builder publishPercentileHistogram() {
            return publishPercentileHistogram(true);
        }

        /**
         * Adds histogram buckets used to generate aggregable percentile approximations in monitoring
         * systems that have query facilities to do so (e.g. Prometheus' {@code histogram_quantile},
         * Atlas' {@code :percentiles}).
         *
         * @param enabled Determines whether percentile histograms should be published.
         * @return This builder.
         */
        public Builder publishPercentileHistogram(@Nullable Boolean enabled) {
            this.distributionConfigBuilder.percentilesHistogram(enabled);
            return this;
        }

        /**
         * Publish at a minimum a histogram containing your defined SLA boundaries. When used in conjunction with
         * {@link Builder#publishPercentileHistogram()}, the boundaries defined here are included alongside
         * other buckets used to generate aggregable percentile approximations.
         *
         * @param sla Publish SLA boundaries in the set of histogram buckets shipped to the monitoring system.
         * @return This builder.
         */
        public Builder sla(@Nullable Duration... sla) {
            if (sla != null) {
                long[] slaNano = new long[sla.length];
                for (int i = 0; i < slaNano.length; i++) {
                    slaNano[i] = sla[i].toNanos();
                }
                this.distributionConfigBuilder.sla(slaNano);
            }
            return this;
        }

        /**
         * Sets the minimum value that this timer is expected to observe. Sets a lower bound
         * on histogram buckets that are shipped to monitoring systems that support aggregable percentile approximations.
         *
         * @param min The minimum value that this timer is expected to observe.
         * @return This builder.
         */
        public Builder minimumExpectedValue(@Nullable Duration min) {
            if (min != null)
                this.distributionConfigBuilder.minimumExpectedValue(min.toNanos());
            return this;
        }

        /**
         * Sets the maximum value that this timer is expected to observe. Sets an upper bound
         * on histogram buckets that are shipped to monitoring systems that support aggregable percentile approximations.
         *
         * @param max The maximum value that this timer is expected to observe.
         * @return This builder.
         */
        public Builder maximumExpectedValue(@Nullable Duration max) {
            if (max != null)
                this.distributionConfigBuilder.maximumExpectedValue(max.toNanos());
            return this;
        }

        /**
         * Statistics emanating from a timer like max, percentiles, and histogram counts decay over time to
         * give greater weight to recent samples (exception: histogram counts are cumulative for those systems that expect cumulative
         * histogram buckets). Samples are accumulated to such statistics in ring buffers which rotate after
         * this expiry, with a buffer length of {@link #distributionStatisticBufferLength(Integer)}.
         *
         * @param expiry The amount of time samples are accumulated to a histogram before it is reset and rotated.
         * @return This builder.
         */
        public Builder distributionStatisticExpiry(@Nullable Duration expiry) {
            this.distributionConfigBuilder.expiry(expiry);
            return this;
        }

        /**
         * Statistics emanating from a timer like max, percentiles, and histogram counts decay over time to
         * give greater weight to recent samples (exception: histogram counts are cumulative for those systems that expect cumulative
         * histogram buckets). Samples are accumulated to such statistics in ring buffers which rotate after
         * {@link #distributionStatisticExpiry(Duration)}, with this buffer length.
         *
         * @param bufferLength The number of histograms to keep in the ring buffer.
         * @return This builder.
         */
        public Builder distributionStatisticBufferLength(@Nullable Integer bufferLength) {
            this.distributionConfigBuilder.bufferLength(bufferLength);
            return this;
        }

        /**
         * Sets the pause detector implementation to use for this timer. Can also be configured on a registry-level with
         * {@link MeterRegistry.Config#pauseDetector(PauseDetector)}.
         *
         * @param pauseDetector The pause detector implementation to use.
         * @return This builder.
         * @see io.micrometer.core.instrument.distribution.pause.NoPauseDetector
         * @see io.micrometer.core.instrument.distribution.pause.ClockDriftPauseDetector
         */
        public Builder pauseDetector(@Nullable PauseDetector pauseDetector) {
            this.pauseDetector = pauseDetector;
            return this;
        }

        /**
         * @param description Description text of the eventual timer.
         * @return This builder.
         */
        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        /**
         * Add the timer to a single registry, or return an existing timer in that registry. The returned
         * timer will be unique for each registry, but each registry is guaranteed to only create one timer
         * for the same combination of name and tags.
         *
         * @param registry A registry to add the timer to, if it doesn't already exist.
         * @return A new or existing timer.
         */
        public Timer register(MeterRegistry registry) {
            // the base unit for a timer will be determined by the monitoring system implementation
            return registry.timer(new Meter.Id(name, tags, null, description, Type.TIMER), distributionConfigBuilder.build(),
                    pauseDetector == null ? registry.config().pauseDetector() : pauseDetector);
        }
    }
}
