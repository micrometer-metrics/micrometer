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
package io.micrometer.core.instrument.distribution;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.config.InvalidConfigurationException;
import io.micrometer.core.instrument.internal.Mergeable;

import java.time.Duration;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.stream.LongStream;

/**
 * Configures the distribution statistics that emanate from meters like
 * {@link io.micrometer.core.instrument.Timer} and
 * {@link io.micrometer.core.instrument.DistributionSummary}.
 * <p>
 * These statistics include max, percentiles, percentile histograms, and SLO violations.
 * <p>
 * Many distribution statistics are decayed to give greater weight to recent samples.
 *
 * @author Jon Schneider
 */
public class DistributionStatisticConfig implements Mergeable<DistributionStatisticConfig> {

    public static final DistributionStatisticConfig DEFAULT = builder().percentilesHistogram(false)
        .percentilePrecision(1)
        .minimumExpectedValue(1.0)
        .maximumExpectedValue(Double.POSITIVE_INFINITY)
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
    private double[] serviceLevelObjectives;

    @Nullable
    private Double minimumExpectedValue;

    @Nullable
    private Double maximumExpectedValue;

    @Nullable
    private Duration expiry;

    @Nullable
    private Integer bufferLength;

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Merges two configurations. Any options that are non-null in this configuration take
     * precedence. Any options that are non-null in the parent are used otherwise.
     * @param parent The configuration to merge with. The parent takes lower precedence
     * than this configuration.
     * @return A new, merged, immutable configuration.
     */
    @Override
    public DistributionStatisticConfig merge(DistributionStatisticConfig parent) {
        return DistributionStatisticConfig.builder()
            .percentilesHistogram(
                    this.percentileHistogram == null ? parent.percentileHistogram : this.percentileHistogram)
            .percentiles(this.percentiles == null ? parent.percentiles : this.percentiles)
            .serviceLevelObjectives(
                    this.serviceLevelObjectives == null ? parent.serviceLevelObjectives : this.serviceLevelObjectives)
            .percentilePrecision(
                    this.percentilePrecision == null ? parent.percentilePrecision : this.percentilePrecision)
            .minimumExpectedValue(
                    this.minimumExpectedValue == null ? parent.minimumExpectedValue : this.minimumExpectedValue)
            .maximumExpectedValue(
                    this.maximumExpectedValue == null ? parent.maximumExpectedValue : this.maximumExpectedValue)
            .expiry(this.expiry == null ? parent.expiry : this.expiry)
            .bufferLength(this.bufferLength == null ? parent.bufferLength : this.bufferLength)
            .build();
    }

    /**
     * For internal use only.
     * @param supportsAggregablePercentiles whether it supports aggregable percentiles
     * @return histogram buckets
     */
    public NavigableSet<Double> getHistogramBuckets(boolean supportsAggregablePercentiles) {
        NavigableSet<Double> buckets = new TreeSet<>();

        if (percentileHistogram != null && percentileHistogram && supportsAggregablePercentiles) {
            buckets.addAll(PercentileHistogramBuckets.buckets(this));
            buckets.add(minimumExpectedValue);
            buckets.add(maximumExpectedValue);
        }

        if (serviceLevelObjectives != null) {
            for (double sloBoundary : serviceLevelObjectives) {
                buckets.add(sloBoundary);
            }
        }

        return buckets;
    }

    /**
     * Adds histogram buckets used to generate aggregable percentile approximations in
     * monitoring systems that have query facilities to do so (e.g. Prometheus'
     * {@code histogram_quantile}, Atlas' {@code :percentiles}).
     * @return This builder.
     */
    @Nullable
    public Boolean isPercentileHistogram() {
        return percentileHistogram;
    }

    /**
     * Produces an additional time series for each requested percentile. This percentile
     * is computed locally, and so can't be aggregated with percentiles computed across
     * other dimensions (e.g. in a different instance). Use {@link #percentileHistogram}
     * to publish a histogram that can be used to generate aggregable percentile
     * approximations.
     * @return Percentiles to compute and publish. The 95th percentile should be expressed
     * as {@code 0.95}
     */
    @Nullable
    public double[] getPercentiles() {
        return percentiles;
    }

    /**
     * Determines the number of digits of precision to maintain on the dynamic range
     * histogram used to compute percentile approximations. The higher the degrees of
     * precision, the more accurate the approximation is at the cost of more memory.
     * @return The digits of precision to maintain for percentile approximations.
     */
    @Nullable
    public Integer getPercentilePrecision() {
        return percentilePrecision;
    }

    /**
     * The minimum value that the meter is expected to observe. Sets a lower bound on
     * histogram buckets that are shipped to monitoring systems that support aggregable
     * percentile approximations.
     * @return The minimum value that this distribution summary is expected to observe.
     * @deprecated Use {@link #getMinimumExpectedValueAsDouble}. If you use this method,
     * your code will not be compatible with code that uses Micrometer 1.3.x.
     */
    @Deprecated
    @Nullable
    public Double getMinimumExpectedValue() {
        return getMinimumExpectedValueAsDouble();
    }

    /**
     * The minimum value that the meter is expected to observe. Sets a lower bound on
     * histogram buckets that are shipped to monitoring systems that support aggregable
     * percentile approximations.
     * @return The minimum value that this distribution summary is expected to observe.
     */
    @Nullable
    public Double getMinimumExpectedValueAsDouble() {
        return minimumExpectedValue;
    }

    /**
     * The maximum value that the meter is expected to observe. Sets an upper bound on
     * histogram buckets that are shipped to monitoring systems that support aggregable
     * percentile approximations.
     * @return The maximum value that the meter is expected to observe.
     * @deprecated Use {@link #getMaximumExpectedValueAsDouble}. If you use this method,
     * your code will not be compatible with code that uses Micrometer 1.3.x.
     */
    @Deprecated
    @Nullable
    public Double getMaximumExpectedValue() {
        return getMaximumExpectedValueAsDouble();
    }

    /**
     * The maximum value that the meter is expected to observe. Sets an upper bound on
     * histogram buckets that are shipped to monitoring systems that support aggregable
     * percentile approximations.
     * @return The maximum value that the meter is expected to observe.
     */
    @Nullable
    public Double getMaximumExpectedValueAsDouble() {
        return maximumExpectedValue;
    }

    /**
     * Statistics like max, percentiles, and histogram counts decay over time to give
     * greater weight to recent samples (exception: histogram counts are cumulative for
     * those systems that expect cumulative histogram buckets). Samples are accumulated to
     * such statistics in ring buffers which rotate after this expiry, with a buffer
     * length of {@link #bufferLength}, hence complete expiry happens after this expiry *
     * buffer length.
     * @return The amount of time samples are accumulated to a histogram before it is
     * reset and rotated.
     */
    @Nullable
    public Duration getExpiry() {
        return expiry;
    }

    /**
     * Statistics like max, percentiles, and histogram counts decay over time to give
     * greater weight to recent samples (exception: histogram counts are cumulative for
     * those systems that expect cumulative histogram buckets). Samples are accumulated to
     * such statistics in ring buffers which rotate after {@link #expiry}, with this
     * buffer length.
     * @return The number of histograms to keep in the ring buffer.
     */
    @Nullable
    public Integer getBufferLength() {
        return bufferLength;
    }

    /**
     * Publish at a minimum a histogram containing your defined SLA boundaries. When used
     * in conjunction with {@link #percentileHistogram}, the boundaries defined here are
     * included alongside other buckets used to generate aggregable percentile
     * approximations. If the {@link DistributionStatisticConfig} is meant for use with a
     * {@link io.micrometer.core.instrument.Timer}, the SLA unit is in nanoseconds.
     * @return The SLA boundaries to include the set of histogram buckets shipped to the
     * monitoring system.
     * @deprecated Use {@link #getServiceLevelObjectiveBoundaries()}. If you use this
     * method, your code will not be compatible with code that uses Micrometer 1.4.x and
     * later.
     */
    @Nullable
    @Deprecated
    public double[] getSlaBoundaries() {
        return getServiceLevelObjectiveBoundaries();
    }

    /**
     * Publish at a minimum a histogram containing your defined SLO boundaries. When used
     * in conjunction with {@link #percentileHistogram}, the boundaries defined here are
     * included alongside other buckets used to generate aggregable percentile
     * approximations. If the {@link DistributionStatisticConfig} is meant for use with a
     * {@link io.micrometer.core.instrument.Timer}, the SLO unit is in nanoseconds.
     * @return The SLO boundaries to include the set of histogram buckets shipped to the
     * monitoring system.
     */
    @Nullable
    public double[] getServiceLevelObjectiveBoundaries() {
        return serviceLevelObjectives;
    }

    public static class Builder {

        private final DistributionStatisticConfig config = new DistributionStatisticConfig();

        public Builder percentilesHistogram(@Nullable Boolean enabled) {
            config.percentileHistogram = enabled;
            return this;
        }

        /**
         * Produces an additional time series for each requested percentile. This
         * percentile is computed locally, and so can't be aggregated with percentiles
         * computed across other dimensions (e.g. in a different instance). Use
         * {@link #percentileHistogram} to publish a histogram that can be used to
         * generate aggregable percentile approximations.
         * @param percentiles Percentiles to compute and publish. The 95th percentile
         * should be expressed as {@code 0.95}.
         * @return This builder.
         */
        public Builder percentiles(@Nullable double... percentiles) {
            config.percentiles = percentiles;
            return this;
        }

        /**
         * Determines the number of digits of precision to maintain on the dynamic range
         * histogram used to compute percentile approximations. The higher the degrees of
         * precision, the more accurate the approximation is at the cost of more memory.
         * @param digitsOfPrecision The digits of precision to maintain for percentile
         * approximations.
         * @return This builder.
         */
        public Builder percentilePrecision(@Nullable Integer digitsOfPrecision) {
            config.percentilePrecision = digitsOfPrecision;
            return this;
        }

        /**
         * Publish at a minimum a histogram containing your defined Service Level
         * Objective (SLO) boundaries. When used in conjunction with
         * {@link #percentileHistogram}, the boundaries defined here are included
         * alongside other buckets used to generate aggregable percentile approximations.
         * If the {@link DistributionStatisticConfig} is meant for use with a
         * {@link io.micrometer.core.instrument.Timer}, the SLO unit is in nanoseconds.
         * @param slos The SLO boundaries to include the set of histogram buckets shipped
         * to the monitoring system.
         * @return This builder.
         * @since 1.5.0
         */
        public Builder serviceLevelObjectives(@Nullable double... slos) {
            config.serviceLevelObjectives = slos;
            return this;
        }

        /**
         * Publish at a minimum a histogram containing your defined SLA boundaries. When
         * used in conjunction with {@link #percentileHistogram}, the boundaries defined
         * here are included alongside other buckets used to generate aggregable
         * percentile approximations. If the {@link DistributionStatisticConfig} is meant
         * for use with a {@link io.micrometer.core.instrument.Timer}, the SLA unit is in
         * nanoseconds.
         * @param sla The SLA boundaries to include the set of histogram buckets shipped
         * to the monitoring system.
         * @return This builder.
         * @since 1.4.0
         * @deprecated Use {@link #serviceLevelObjectives(double...)} instead. "Service
         * Level Agreement" is more formally the agreement between an engineering
         * organization and the business. Service Level Objectives are set more
         * conservatively than the SLA to provide some wiggle room while still satisfying
         * the business requirement. SLOs are the threshold we intend to measure against,
         * then.
         */
        @Deprecated
        public Builder sla(@Nullable double... sla) {
            return serviceLevelObjectives(sla);
        }

        /**
         * Publish at a minimum a histogram containing your defined SLA boundaries. When
         * used in conjunction with {@link #percentileHistogram}, the boundaries defined
         * here are included alongside other buckets used to generate aggregable
         * percentile approximations. If the {@link DistributionStatisticConfig} is meant
         * for use with a {@link io.micrometer.core.instrument.Timer}, the SLA unit is in
         * nanoseconds.
         * @param sla The SLA boundaries to include the set of histogram buckets shipped
         * to the monitoring system.
         * @return This builder.
         * @deprecated Use {@link #serviceLevelObjectives(double...)} instead. "Service
         * Level Agreement" is more formally the agreement between an engineering
         * organization and the business. Service Level Objectives are set more
         * conservatively than the SLA to provide some wiggle room while still satisfying
         * the business requirement. SLOs are the threshold we intend to measure against,
         * then.
         */
        @Deprecated
        public Builder sla(@Nullable long... sla) {
            return sla == null ? this : serviceLevelObjectives(LongStream.of(sla).asDoubleStream().toArray());
        }

        /**
         * The minimum value that the meter is expected to observe. Sets a lower bound on
         * histogram buckets that are shipped to monitoring systems that support
         * aggregable percentile approximations.
         * @deprecated Use {@link #minimumExpectedValue(Double)} instead since 1.4.0.
         * @param min The minimum value that this distribution summary is expected to
         * observe.
         * @return This builder.
         */
        @Deprecated
        public Builder minimumExpectedValue(@Nullable Long min) {
            return min == null ? this : minimumExpectedValue((double) min);
        }

        /**
         * The minimum value that the meter is expected to observe. Sets a lower bound on
         * histogram buckets that are shipped to monitoring systems that support
         * aggregable percentile approximations.
         * @param min The minimum value that this distribution summary is expected to
         * observe.
         * @return This builder.
         * @since 1.3.10
         */
        public Builder minimumExpectedValue(@Nullable Double min) {
            config.minimumExpectedValue = min;
            return this;
        }

        /**
         * The maximum value that the meter is expected to observe. Sets an upper bound on
         * histogram buckets that are shipped to monitoring systems that support
         * aggregable percentile approximations.
         * @deprecated Use {@link #maximumExpectedValue(Double)} instead since 1.4.0.
         * @param max The maximum value that the meter is expected to observe.
         * @return This builder.
         */
        @Deprecated
        public Builder maximumExpectedValue(@Nullable Long max) {
            return max == null ? this : maximumExpectedValue((double) max);
        }

        /**
         * The maximum value that the meter is expected to observe. Sets an upper bound on
         * histogram buckets that are shipped to monitoring systems that support
         * aggregable percentile approximations.
         * @param max The maximum value that the meter is expected to observe.
         * @return This builder.
         * @since 1.3.10
         */
        public Builder maximumExpectedValue(@Nullable Double max) {
            config.maximumExpectedValue = max;
            return this;
        }

        /**
         * Statistics like max, percentiles, and histogram counts decay over time to give
         * greater weight to recent samples (exception: histogram counts are cumulative
         * for those systems that expect cumulative histogram buckets). Samples are
         * accumulated to such statistics in ring buffers which rotate after this expiry,
         * with a buffer length of {@link #bufferLength}, hence complete expiry happens
         * after this expiry * buffer length.
         * @param expiry The amount of time samples are accumulated to decaying
         * distribution statistics before they are reset and rotated.
         * @return This builder.
         */
        public Builder expiry(@Nullable Duration expiry) {
            config.expiry = expiry;
            return this;
        }

        /**
         * Statistics like max, percentiles, and histogram counts decay over time to give
         * greater weight to recent samples (exception: histogram counts are cumulative
         * for those systems that expect cumulative histogram buckets). Samples are
         * accumulated to such statistics in ring buffers which rotate after
         * {@link #expiry}, with this buffer length.
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
            validate(config);
            return config;
        }

        private void validate(DistributionStatisticConfig distributionStatisticConfig) {
            if (config.bufferLength != null && config.bufferLength <= 0) {
                rejectConfig("bufferLength (" + config.bufferLength + ") must be greater than zero");
            }

            if (config.percentiles != null) {
                for (double p : config.percentiles) {
                    if (p < 0 || p > 1) {
                        rejectConfig("percentiles must contain only the values between 0.0 and 1.0. " + "Found " + p);
                    }
                }
            }

            if (config.minimumExpectedValue != null && config.minimumExpectedValue <= 0) {
                rejectConfig("minimumExpectedValue (" + config.minimumExpectedValue + ") must be greater than 0.");
            }

            if (config.maximumExpectedValue != null && config.maximumExpectedValue <= 0) {
                rejectConfig("maximumExpectedValue (" + config.maximumExpectedValue + ") must be greater than 0.");
            }

            if ((config.minimumExpectedValue != null && config.maximumExpectedValue != null)
                    && config.minimumExpectedValue > config.maximumExpectedValue) {
                rejectConfig("maximumExpectedValue (" + config.maximumExpectedValue
                        + ") must be equal to or greater than minimumExpectedValue (" + config.minimumExpectedValue
                        + ").");
            }

            if (distributionStatisticConfig.getServiceLevelObjectiveBoundaries() != null) {
                for (double slo : distributionStatisticConfig.getServiceLevelObjectiveBoundaries()) {
                    if (slo <= 0) {
                        rejectConfig("serviceLevelObjectiveBoundaries must contain only the values greater than 0. "
                                + "Found " + slo);
                    }
                }
            }
        }

        private static void rejectConfig(String msg) {
            throw new InvalidConfigurationException("Invalid distribution configuration: " + msg);
        }

    }

    public boolean isPublishingPercentiles() {
        return percentiles != null && percentiles.length > 0;
    }

    public boolean isPublishingHistogram() {
        return (percentileHistogram != null && percentileHistogram)
                || (serviceLevelObjectives != null && serviceLevelObjectives.length > 0);
    }

}
