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
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.histogram.pause.PauseDetector;
import io.micrometer.core.lang.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Timer intended to track of a large number of short running events. Example would be something like
 * an HTTP request. Though "short running" is a bit subjective the assumption is that it should be
 * under a minute.
 */
public interface Timer extends Meter {
    /**
     * Updates the statistics kept by the counter with the specified amount.
     *
     * @param amount Duration of a single event being measured by this timer. If the amount is less than 0
     *               the value will be dropped.
     * @param unit   Time unit for the amount being recorded.
     */
    void record(long amount, TimeUnit unit);

    /**
     * Updates the statistics kept by the counter with the specified amount.
     *
     * @param duration Duration of a single event being measured by this timer.
     */
    default void record(Duration duration) {
        record(duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    /**
     * Executes the Supplier `f` and records the time taken.
     *
     * @param f Function to execute and measure the execution time.
     * @return The return value of `f`.
     */
    <T> T record(Supplier<T> f);

    /**
     * Executes the callable `f` and records the time taken.
     *
     * @param f Function to execute and measure the execution time.
     * @return The return value of `f`.
     */
    <T> T recordCallable(Callable<T> f) throws Exception;

    /**
     * Executes the runnable `f` and records the time taken.
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
     * @param f The Callable to time when it is invoked.
     * @return The wrapped Callable.
     */
    default <T> Callable<T> wrap(Callable<T> f) {
        return () -> recordCallable(f);
    }

    /**
     * The number of times that stop has been called on this timer.
     */
    long count();

    /**
     * The total time of recorded events.
     */
    double totalTime(TimeUnit unit);

    default double mean(TimeUnit unit) {
        return count() == 0 ? 0 : totalTime(unit) / count();
    }

    /**
     * The maximum time of a single event.
     */
    double max(TimeUnit unit);

    /**
     * The latency at a specific percentile. This value is non-aggregable across dimensions.
     */
    double percentile(double percentile, TimeUnit unit);

    double histogramCountAtValue(long valueNanos);

    HistogramSnapshot takeSnapshot(boolean supportsAggregablePercentiles);

    @Override
    default Iterable<Measurement> measure() {
        return Arrays.asList(
            new Measurement(() -> (double) count(), Statistic.Count),
            new Measurement(() -> totalTime(baseTimeUnit()), Statistic.TotalTime),
            new Measurement(() -> max(baseTimeUnit()), Statistic.Max)
        );
    }

    TimeUnit baseTimeUnit();

    @Override
    default Type type() {
        return Type.Timer;
    }

    static Sample start(MeterRegistry registry) {
        return new Sample(registry.config().clock());
    }

    static Sample start(Clock clock) {
        return new Sample(clock);
    }

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
         * @return The total duration of the sample in nanoseconds
         */
        public long stop(Timer timer) {
            long durationNs = clock.monotonicTime() - startTime;
            timer.record(durationNs, TimeUnit.NANOSECONDS);
            return durationNs;
        }
    }

    static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Create a timer builder from a {@link Timed} annotation.
     *
     * @param timed       The annotation instance to base a new timer on.
     * @param defaultName A default name to use in the event that the value attribute is empty.
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

    class Builder {
        private final String name;
        private final List<Tag> tags = new ArrayList<>();
        @Nullable
        private String description;
        private final HistogramConfig.Builder histogramConfigBuilder;
        @Nullable
        private PauseDetector pauseDetector;

        private Builder(String name) {
            this.name = name;
            this.histogramConfigBuilder = new HistogramConfig.Builder();
            minimumExpectedValue(Duration.ofMillis(1));
            maximumExpectedValue(Duration.ofSeconds(30));
        }

        public Builder tags(Iterable<Tag> tags) {
            tags.forEach(this.tags::add);
            return this;
        }

        public Builder tag(String key, String value) {
            tags.add(Tag.of(key, value));
            return this;
        }

        /**
         * Produces an additional time series for each requested percentile. This percentile
         * is computed locally, and so can't be aggregated with percentiles computed across other
         * dimensions (e.g. in a different instance). Use {@link #publishPercentileHistogram()}
         * to publish a histogram that can be used to generate aggregable percentile approximations.
         *
         * @param percentiles Percentiles to compute and publish. The 95th percentile should be expressed as {@code 95.0}
         */
        public Builder publishPercentiles(@Nullable double... percentiles) {
            this.histogramConfigBuilder.percentiles(percentiles);
            return this;
        }

        /**
         * Adds histogram buckets usable for generating aggregable percentile approximations in monitoring
         * systems that have query facilities to do so (e.g. Prometheus' {@code histogram_quantile},
         * Atlas' {@code :percentiles}).
         */
        public Builder publishPercentileHistogram() {
            return publishPercentileHistogram(true);
        }

        /**
         * Adds histogram buckets usable for generating aggregable percentile approximations in monitoring
         * systems that have query facilities to do so (e.g. Prometheus' {@code histogram_quantile},
         * Atlas' {@code :percentiles}).
         */
        public Builder publishPercentileHistogram(@Nullable Boolean enabled) {
            this.histogramConfigBuilder.percentilesHistogram(enabled);
            return this;
        }

        /**
         * Publish at a minimum a histogram containing your defined SLA boundaries. When used in conjunction with
         * {@link Builder#publishPercentileHistogram()}, the boundaries defined here are included alongside
         * other buckets used to generate aggregable percentile approximations.
         *
         * @param sla Publish SLA boundaries in the set of histogram buckets shipped to the monitoring system.
         */
        public Builder sla(@Nullable Duration... sla) {
            if (sla != null) {
                long[] slaNano = new long[sla.length];
                for (int i = 0; i < slaNano.length; i++) {
                    slaNano[i] = sla[i].toNanos();
                }
                this.histogramConfigBuilder.sla(slaNano);
            }
            return this;
        }

        public Builder minimumExpectedValue(@Nullable Duration min) {
            if (min != null)
                this.histogramConfigBuilder.minimumExpectedValue(min.toNanos());
            return this;
        }

        public Builder maximumExpectedValue(@Nullable Duration max) {
            if (max != null)
                this.histogramConfigBuilder.maximumExpectedValue(max.toNanos());
            return this;
        }

        public Builder histogramExpiry(@Nullable Duration expiry) {
            this.histogramConfigBuilder.histogramExpiry(expiry);
            return this;
        }

        public Builder histogramBufferLength(@Nullable Integer bufferLength) {
            this.histogramConfigBuilder.histogramBufferLength(bufferLength);
            return this;
        }

        public Builder pauseDetector(@Nullable PauseDetector pauseDetector) {
            this.pauseDetector = pauseDetector;
            return this;
        }

        /**
         * @param tags Must be an even number of arguments representing key/value pairs of tags.
         */
        public Builder tags(String... tags) {
            return tags(Tags.zip(tags));
        }

        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        public Timer register(MeterRegistry registry) {
            // the base unit for a timer will be determined by the monitoring system implementation
            return registry.timer(new Meter.Id(name, tags, null, description, Type.Timer), histogramConfigBuilder.build(),
                pauseDetector == null ? registry.config().pauseDetector() : pauseDetector);
        }
    }
}
