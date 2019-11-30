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
package io.micrometer.core.instrument.distribution;

import io.micrometer.core.instrument.internal.Mergeable;
import io.micrometer.core.lang.Nullable;

import java.time.Duration;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Configures the distribution statistics that emanate from meters like {@link io.micrometer.core.instrument.Timer}
 * and {@link io.micrometer.core.instrument.DistributionSummary}.
 * <p>
 * These statistics include max, percentiles, percentile histograms, and SLA violations.
 * <p>
 * Many distribution statistics are decayed to give greater weight to recent samples.
 *
 * @author Jon Schneider
 */
public class DistributionStatisticConfig implements Mergeable<DistributionStatisticConfig> {
    public static final DistributionStatisticConfig DEFAULT = builder()
            .percentilesHistogram(false)
            .percentilePrecision(1)
            .minimumExpectedValue(1L)
            .maximumExpectedValue(Long.MAX_VALUE)
            .expiry(Duration.ofMinutes(2))
            .bufferLength(3)
            .build();

    public static final DistributionStatisticConfig NONE = builder().build();

    @Nullable
    private Boolean percentileHistogram;

    @Nullable
    private double[] percentiles;

    @Nullable
    private Integer percentilePrecision;

    @Nullable
    private long[] sla;

    @Nullable
    private Long minimumExpectedValue;

    @Nullable
    private Long maximumExpectedValue;

    @Nullable
    private Duration expiry;

    @Nullable
    private Integer bufferLength;

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Merges two configurations. Any options that are non-null in this configuration take precedence.
     * Any options that are non-null in the parent are used otherwise.
     *
     * @param parent The configuration to merge with. The parent takes lower precedence than this configuration.
     * @return A new, merged, immutable configuration.
     */
    @Override
    public DistributionStatisticConfig merge(DistributionStatisticConfig parent) {
        return DistributionStatisticConfig.builder()
                .percentilesHistogram(this.percentileHistogram == null ? parent.percentileHistogram : this.percentileHistogram)
                .percentiles(this.percentiles == null ? parent.percentiles : this.percentiles)
                .sla(this.sla == null ? parent.sla : this.sla)
                .percentilePrecision(this.percentilePrecision == null ? parent.percentilePrecision : this.percentilePrecision)
                .minimumExpectedValue(this.minimumExpectedValue == null ? parent.minimumExpectedValue : this.minimumExpectedValue)
                .maximumExpectedValue(this.maximumExpectedValue == null ? parent.maximumExpectedValue : this.maximumExpectedValue)
                .expiry(this.expiry == null ? parent.expiry : this.expiry)
                .bufferLength(this.bufferLength == null ? parent.bufferLength : this.bufferLength)
                .build();
    }

    public NavigableSet<Long> getHistogramBuckets(boolean supportsAggregablePercentiles) {
        NavigableSet<Long> buckets = new TreeSet<>();

        if (percentileHistogram != null && percentileHistogram && supportsAggregablePercentiles) {
            buckets.addAll(PercentileHistogramBuckets.buckets(this));
            buckets.add(minimumExpectedValue);
            buckets.add(maximumExpectedValue);
        }

        if (sla != null) {
            for (long slaBoundary : sla) {
                buckets.add(slaBoundary);
            }
        }

        return buckets;
    }

    /**
     * Adds histogram buckets used to generate aggregable percentile approximations in monitoring
     * systems that have query facilities to do so (e.g. Prometheus' {@code histogram_quantile},
     * Atlas' {@code :percentiles}).
     *
     * @return This builder.
     */
    @Nullable
    public Boolean isPercentileHistogram() {
        return percentileHistogram;
    }

    /**
     * Produces an additional time series for each requested percentile. This percentile
     * is computed locally, and so can't be aggregated with percentiles computed across other
     * dimensions (e.g. in a different instance). Use {@link #percentileHistogram}
     * to publish a histogram that can be used to generate aggregable percentile approximations.
     *
     * @return Percentiles to compute and publish. The 95th percentile should be expressed as {@code 0.95}
     */
    @Nullable
    public double[] getPercentiles() {
        return percentiles;
    }

    /**
     * Determines the number of digits of precision to maintain on the dynamic range histogram used to compute
     * percentile approximations. The higher the degrees of precision, the more accurate the approximation is at the
     * cost of more memory.
     *
     * @return The digits of precision to maintain for percentile approximations.
     */
    @Nullable
    public Integer getPercentilePrecision() {
        return percentilePrecision;
    }

    /**
     * The minimum value that the meter is expected to observe. Sets a lower bound
     * on histogram buckets that are shipped to monitoring systems that support aggregable percentile approximations.
     *
     * @return The minimum value that this distribution summary is expected to observe.
     */
    @Nullable
    public Long getMinimumExpectedValue() {
        return minimumExpectedValue;
    }

    /**
     * The maximum value that the meter is expected to observe. Sets an upper bound
     * on histogram buckets that are shipped to monitoring systems that support aggregable percentile approximations.
     *
     * @return The maximum value that the meter is expected to observe.
     */
    @Nullable
    public Long getMaximumExpectedValue() {
        return maximumExpectedValue;
    }

    /**
     * Statistics like max, percentiles, and histogram counts decay over time to give greater weight to recent
     * samples (exception: histogram counts are cumulative for those systems that expect cumulative
     * histogram buckets). Samples are accumulated to such statistics in ring buffers which rotate after
     * this expiry, with a buffer length of {@link #bufferLength}.
     *
     * @return The amount of time samples are accumulated to a histogram before it is reset and rotated.
     */
    @Nullable
    public Duration getExpiry() {
        return expiry;
    }

    /**
     * Statistics like max, percentiles, and histogram counts decay over time to give greater weight to recent
     * samples (exception: histogram counts are cumulative for those systems that expect cumulative
     * histogram buckets). Samples are accumulated to such statistics in ring buffers which rotate after
     * {@link #expiry}, with this buffer length.
     *
     * @return The number of histograms to keep in the ring buffer.
     */
    @Nullable
    public Integer getBufferLength() {
        return bufferLength;
    }

    /**
     * Publish at a minimum a histogram containing your defined SLA boundaries. When used in conjunction with
     * {@link #percentileHistogram}, the boundaries defined here are included alongside other buckets used to
     * generate aggregable percentile approximations.  If The {@link DistributionStatisticConfig} is meant for
     * use with a {@link io.micrometer.core.instrument.Timer}, the SLA unit is in nanoseconds
     *
     * @return The SLA boundaries to include the set of histogram buckets shipped to the monitoring system.
     */
    @Nullable
    public long[] getSlaBoundaries() {
        return sla;
    }

    public static class Builder {
        private final DistributionStatisticConfig config = new DistributionStatisticConfig();

        public Builder percentilesHistogram(@Nullable Boolean enabled) {
            config.percentileHistogram = enabled;
            return this;
        }

        /**
         * Produces an additional time series for each requested percentile. This percentile
         * is computed locally, and so can't be aggregated with percentiles computed across other
         * dimensions (e.g. in a different instance). Use {@link #percentileHistogram}
         * to publish a histogram that can be used to generate aggregable percentile approximations.
         *
         * @param percentiles Percentiles to compute and publish. The 95th percentile should be expressed as {@code 0.95}.
         * @return This builder.
         */
        public Builder percentiles(@Nullable double... percentiles) {
            config.percentiles = percentiles;
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
            config.percentilePrecision = digitsOfPrecision;
            return this;
        }

        /**
         * Publish at a minimum a histogram containing your defined SLA boundaries. When used in conjunction with
         * {@link #percentileHistogram}, the boundaries defined here are included alongside other buckets used to
         * generate aggregable percentile approximations.  If The {@link DistributionStatisticConfig} is meant for
         * use with a {@link io.micrometer.core.instrument.Timer}, the SLA unit is in nanoseconds
         *
         * @param sla The SLA boundaries to include the set of histogram buckets shipped to the monitoring system.
         * @return This builder.
         */
        public Builder sla(@Nullable long... sla) {
            config.sla = sla;
            return this;
        }

        /**
         * The minimum value that the meter is expected to observe. Sets a lower bound
         * on histogram buckets that are shipped to monitoring systems that support aggregable percentile approximations.
         *
         * @param min The minimum value that this distribution summary is expected to observe.
         * @return This builder.
         */
        public Builder minimumExpectedValue(@Nullable Long min) {
            config.minimumExpectedValue = min;
            return this;
        }

        /**
         * The maximum value that the meter is expected to observe. Sets an upper bound
         * on histogram buckets that are shipped to monitoring systems that support aggregable percentile approximations.
         *
         * @param max The maximum value that the meter is expected to observe.
         * @return This builder.
         */
        public Builder maximumExpectedValue(@Nullable Long max) {
            config.maximumExpectedValue = max;
            return this;
        }

        /**
         * Statistics like max, percentiles, and histogram counts decay over time to give greater weight to recent
         * samples (exception: histogram counts are cumulative for those systems that expect cumulative
         * histogram buckets). Samples are accumulated to such statistics in ring buffers which rotate after
         * this expiry, with a buffer length of {@link #bufferLength}.
         *
         * @param expiry The amount of time samples are accumulated to decaying distribution statistics before they are
         *               reset and rotated.
         * @return This builder.
         */
        public Builder expiry(@Nullable Duration expiry) {
            config.expiry = expiry;
            return this;
        }

        /**
         * Statistics like max, percentiles, and histogram counts decay over time to give greater weight to recent
         * samples (exception: histogram counts are cumulative for those systems that expect cumulative
         * histogram buckets). Samples are accumulated to such statistics in ring buffers which rotate after
         * {@link #expiry}, with this buffer length.
         *
         * @param bufferLength The number of histograms to keep in the ring buffer.
         * @return This builder.
         */
        public Builder bufferLength(@Nullable Integer bufferLength) {
            config.bufferLength = bufferLength;
            return this;
        }

        /**
         * @return A new immutable distribution configuration.
         */
        public DistributionStatisticConfig build() {
            return config;
        }
    }

    public boolean isPublishingPercentiles() {
        return percentiles != null && percentiles.length > 0;
    }

    public boolean isPublishingHistogram() {
        return (percentileHistogram != null && percentileHistogram) || (sla != null && sla.length > 0);
    }
}
