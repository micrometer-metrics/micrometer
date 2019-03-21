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

import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSupport;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.lang.Nullable;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Track the sample distribution of events. An example would be the response sizes for requests
 * hitting an http server.
 *
 * @author Jon Schneider
 */
public interface DistributionSummary extends Meter, HistogramSupport {

    static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Updates the statistics kept by the summary with the specified amount.
     *
     * @param amount Amount for an event being measured. For example, if the size in bytes of responses
     *               from a server. If the amount is less than 0 the value will be dropped.
     */
    void record(double amount);

    /**
     * @return The number of times that record has been called since this timer was created.
     */
    long count();

    /**
     * @return The total amount of all recorded events.
     */
    double totalAmount();

    /**
     * @return The distribution average for all recorded events.
     */
    default double mean() {
        return count() == 0 ? 0 : totalAmount() / count();
    }

    /**
     * @return The maximum time of a single event.
     */
    double max();

    /**
     * Provides cumulative histogram counts.
     *
     * @param value The histogram bucket to retrieve a count for.
     * @return The count of all events less than or equal to the bucket. If value does not
     * match a preconfigured bucket boundary, returns NaN.
     * @deprecated Use {@link #takeSnapshot()} to retrieve bucket counts.
     */
    @Deprecated
    default double histogramCountAtValue(long value) {
        for (CountAtBucket countAtBucket : takeSnapshot().histogramCounts()) {
            if ((long) countAtBucket.bucket(TimeUnit.NANOSECONDS) == value) {
                return countAtBucket.count();
            }
        }
        return Double.NaN;
    }

    /**
     * @param percentile A percentile in the domain [0, 1]. For example, 0.5 represents the 50th percentile of the
     *                   distribution.
     * @return The latency at a specific percentile. This value is non-aggregable across dimensions. Returns NaN if
     * percentile is not a preconfigured percentile that Micrometer is tracking.
     * @deprecated Use {@link #takeSnapshot()} to retrieve percentiles.
     */
    @Deprecated
    default double percentile(double percentile) {
        for (ValueAtPercentile valueAtPercentile : takeSnapshot().percentileValues()) {
            if (valueAtPercentile.percentile() == percentile) {
                return valueAtPercentile.value();
            }
        }
        return Double.NaN;
    }

    @Override
    default Iterable<Measurement> measure() {
        return Arrays.asList(
                new Measurement(() -> (double) count(), Statistic.COUNT),
                new Measurement(this::totalAmount, Statistic.TOTAL)
        );
    }

    /**
     * Fluent builder for distribution summaries.
     */
    class Builder {
        private final String name;
        private Tags tags = Tags.empty();
        private DistributionStatisticConfig.Builder distributionConfigBuilder = DistributionStatisticConfig.builder();

        @Nullable
        private String description;

        @Nullable
        private String baseUnit;

        private double scale = 1.0;

        private Builder(String name) {
            this.name = name;
        }

        /**
         * @param tags Must be an even number of arguments representing key/value pairs of tags.
         * @return The distribution summmary builder with added tags.
         */
        public Builder tags(String... tags) {
            return tags(Tags.of(tags));
        }

        /**
         * @param tags Tags to add to the eventual distribution summary.
         * @return The distribution summary builder with added tags.
         */
        public Builder tags(Iterable<Tag> tags) {
            this.tags = this.tags.and(tags);
            return this;
        }

        /**
         * @param key   The tag key.
         * @param value The tag value.
         * @return The distribution summary builder with a single added tag.
         */
        public Builder tag(String key, String value) {
            this.tags = tags.and(key, value);
            return this;
        }
        /**
         * @param description Description text of the eventual distribution summary.
         * @return The distribution summary builder with added description.
         */
        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        /**
         * @param unit Base unit of the eventual distribution summary.
         * @return The distribution summary builder with added base unit.
         */
        public Builder baseUnit(@Nullable String unit) {
            this.baseUnit = unit;
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
        public Builder sla(@Nullable long... sla) {
            this.distributionConfigBuilder.sla(sla);
            return this;
        }

        /**
         * Sets the minimum value that this distribution summary is expected to observe. Sets a lower bound
         * on histogram buckets that are shipped to monitoring systems that support aggregable percentile approximations.
         *
         * @param min The minimum value that this distribution summary is expected to observe.
         * @return This builder.
         */
        public Builder minimumExpectedValue(@Nullable Long min) {
            this.distributionConfigBuilder.minimumExpectedValue(min);
            return this;
        }

        /**
         * Sets the maximum value that this distribution summary is expected to observe. Sets an upper bound
         * on histogram buckets that are shipped to monitoring systems that support aggregable percentile approximations.
         *
         * @param max The maximum value that this distribution summary is expected to observe.
         * @return This builder.
         */
        public Builder maximumExpectedValue(@Nullable Long max) {
            this.distributionConfigBuilder.maximumExpectedValue(max);
            return this;
        }

        /**
         * Statistics emanating from a distribution summary like max, percentiles, and histogram counts decay over time to
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
         * Statistics emanating from a distribution summary like max, percentiles, and histogram counts decay over time to
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
         * Multiply values recorded to the distribution summary by a scaling factor.
         *
         * @param scale Factor to scale each recorded value by.
         * @return This builder.
         */
        public Builder scale(double scale) {
            this.scale = scale;
            return this;
        }

        /**
         * Add the distribution summary to a single registry, or return an existing distribution summary in that registry. The returned
         * distribution summary will be unique for each registry, but each registry is guaranteed to only create one distribution summary
         * for the same combination of name and tags.
         *
         * @param registry A registry to add the distribution summary to, if it doesn't already exist.
         * @return A new or existing distribution summary.
         */
        public DistributionSummary register(MeterRegistry registry) {
            return registry.summary(new Meter.Id(name, tags, baseUnit, description, Type.DISTRIBUTION_SUMMARY), distributionConfigBuilder.build(), scale);
        }
    }

}
