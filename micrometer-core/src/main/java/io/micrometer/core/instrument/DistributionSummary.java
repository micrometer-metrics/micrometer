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

import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.lang.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Track the sample distribution of events. An example would be the response sizes for requests
 * hitting and http server.
 */
public interface DistributionSummary extends Meter {

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
     * The number of times that record has been called since this timer was created.
     */
    long count();

    /**
     * The total amount of all recorded events since this summary was created.
     */
    double totalAmount();

    default double mean() {
        return count() == 0 ? 0 : totalAmount() / count();
    }

    /**
     * The maximum time of a single event.
     */
    double max();

    /**
     * The value at a specific percentile. This value is non-aggregable across dimensions.
     */
    double percentile(double percentile);

    double histogramCountAtValue(long value);

    HistogramSnapshot takeSnapshot(boolean supportsAggregablePercentiles);

    @Override
    default Iterable<Measurement> measure() {
        return Arrays.asList(
            new Measurement(() -> (double) count(), Statistic.Count),
            new Measurement(this::totalAmount, Statistic.Total)
        );
    }

    class Builder {
        private final String name;
        private final List<Tag> tags = new ArrayList<>();
        @Nullable
        private String description;
        @Nullable
        private String baseUnit;
        private HistogramConfig.Builder histogramConfigBuilder = HistogramConfig.builder();

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
        public Builder sla(@Nullable long... sla) {
            this.histogramConfigBuilder.sla(sla);
            return this;
        }

        public Builder minimumExpectedValue(@Nullable Long min) {
            this.histogramConfigBuilder.minimumExpectedValue(min);
            return this;
        }

        public Builder maximumExpectedValue(@Nullable Long max) {
            this.histogramConfigBuilder.maximumExpectedValue(max);
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

        public DistributionSummary register(MeterRegistry registry) {
            return registry.summary(new Meter.Id(name, tags, baseUnit, description, Type.DistributionSummary), histogramConfigBuilder.build());
        }
    }

}
