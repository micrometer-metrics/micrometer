/*
 * Copyright 2020 VMware, Inc.
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
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;

import java.time.Duration;
import java.util.Arrays;

/**
 * Base builder for {@link Timer}.
 *
 * @param <B> builder type
 * @author Jon Schneider
 * @since 1.6.0
 */
@SuppressWarnings("unchecked")
public abstract class AbstractTimerBuilder<B extends AbstractTimerBuilder<B>> {

    protected final String name;

    protected Tags tags = Tags.empty();

    protected final DistributionStatisticConfig.Builder distributionConfigBuilder;

    @Nullable
    protected String description;

    @Nullable
    protected PauseDetector pauseDetector;

    protected AbstractTimerBuilder(String name) {
        this.name = name;
        this.distributionConfigBuilder = new DistributionStatisticConfig.Builder();
        minimumExpectedValue(Duration.ofMillis(1));
        maximumExpectedValue(Duration.ofSeconds(30));
    }

    /**
     * @param tags Must be an even number of arguments representing key/value pairs of
     * tags.
     * @return This builder.
     */
    public B tags(String... tags) {
        return tags(Tags.of(tags));
    }

    /**
     * @param tags Tags to add to the eventual timer.
     * @return The timer builder with added tags.
     */
    public B tags(Iterable<Tag> tags) {
        this.tags = this.tags.and(tags);
        return (B) this;
    }

    /**
     * @param key The tag key.
     * @param value The tag value.
     * @return The timer builder with a single added tag.
     */
    public B tag(String key, String value) {
        this.tags = tags.and(key, value);
        return (B) this;
    }

    /**
     * Produces an additional time series for each requested percentile. This percentile
     * is computed locally, and so can't be aggregated with percentiles computed across
     * other dimensions (e.g. in a different instance). Use
     * {@link #publishPercentileHistogram()} to publish a histogram that can be used to
     * generate aggregable percentile approximations.
     * @param percentiles Percentiles to compute and publish. The 95th percentile should
     * be expressed as {@code 0.95}.
     * @return This builder.
     */
    public B publishPercentiles(@Nullable double... percentiles) {
        this.distributionConfigBuilder.percentiles(percentiles);
        return (B) this;
    }

    /**
     * Determines the number of digits of precision to maintain on the dynamic range
     * histogram used to compute percentile approximations. The higher the degrees of
     * precision, the more accurate the approximation is at the cost of more memory.
     * @param digitsOfPrecision The digits of precision to maintain for percentile
     * approximations.
     * @return This builder.
     */
    public B percentilePrecision(@Nullable Integer digitsOfPrecision) {
        this.distributionConfigBuilder.percentilePrecision(digitsOfPrecision);
        return (B) this;
    }

    /**
     * Adds histogram buckets used to generate aggregable percentile approximations in
     * monitoring systems that have query facilities to do so (e.g. Prometheus'
     * {@code histogram_quantile}, Atlas' {@code :percentiles}).
     * @return This builder.
     */
    public B publishPercentileHistogram() {
        return publishPercentileHistogram(true);
    }

    /**
     * Adds histogram buckets used to generate aggregable percentile approximations in
     * monitoring systems that have query facilities to do so (e.g. Prometheus'
     * {@code histogram_quantile}, Atlas' {@code :percentiles}).
     * @param enabled Determines whether percentile histograms should be published.
     * @return This builder.
     */
    public B publishPercentileHistogram(@Nullable Boolean enabled) {
        this.distributionConfigBuilder.percentilesHistogram(enabled);
        return (B) this;
    }

    /**
     * Publish at a minimum a histogram containing your defined service level objective
     * (SLO) boundaries. When used in conjunction with
     * {@link AbstractTimerBuilder#publishPercentileHistogram()}, the boundaries defined
     * here are included alongside other buckets used to generate aggregable percentile
     * approximations.
     * @param sla Publish SLO boundaries in the set of histogram buckets shipped to the
     * monitoring system.
     * @return This builder.
     * @deprecated Use {{@link #serviceLevelObjectives(Duration...)}} instead. "Service
     * Level Agreement" is more formally the agreement between an engineering organization
     * and the business. Service Level Objectives are set more conservatively than the SLA
     * to provide some wiggle room while still satisfying the business requirement. SLOs
     * are the threshold we intend to measure against, then.
     */
    @Deprecated
    public B sla(@Nullable Duration... sla) {
        return serviceLevelObjectives(sla);
    }

    /**
     * Publish at a minimum a histogram containing your defined service level objective
     * (SLO) boundaries. When used in conjunction with
     * {@link AbstractTimerBuilder#publishPercentileHistogram()}, the boundaries defined
     * here are included alongside other buckets used to generate aggregable percentile
     * approximations.
     * @param slos Publish SLO boundaries in the set of histogram buckets shipped to the
     * monitoring system.
     * @return This builder.
     * @since 1.5.0
     */
    public B serviceLevelObjectives(@Nullable Duration... slos) {
        if (slos != null) {
            this.distributionConfigBuilder
                .serviceLevelObjectives(Arrays.stream(slos).mapToDouble(Duration::toNanos).toArray());
        }
        return (B) this;
    }

    /**
     * Sets the minimum value that this timer is expected to observe. Sets a lower bound
     * on histogram buckets that are shipped to monitoring systems that support aggregable
     * percentile approximations.
     * @param min The minimum value that this timer is expected to observe.
     * @return This builder.
     */
    public B minimumExpectedValue(@Nullable Duration min) {
        if (min != null)
            this.distributionConfigBuilder.minimumExpectedValue((double) min.toNanos());
        return (B) this;
    }

    /**
     * Sets the maximum value that this timer is expected to observe. Sets an upper bound
     * on histogram buckets that are shipped to monitoring systems that support aggregable
     * percentile approximations.
     * @param max The maximum value that this timer is expected to observe.
     * @return This builder.
     */
    public B maximumExpectedValue(@Nullable Duration max) {
        if (max != null)
            this.distributionConfigBuilder.maximumExpectedValue((double) max.toNanos());
        return (B) this;
    }

    /**
     * Statistics emanating from a timer like max, percentiles, and histogram counts decay
     * over time to give greater weight to recent samples (exception: histogram counts are
     * cumulative for those systems that expect cumulative histogram buckets). Samples are
     * accumulated to such statistics in ring buffers which rotate after this expiry, with
     * a buffer length of {@link #distributionStatisticBufferLength(Integer)}, hence
     * complete expiry happens after this expiry * buffer length.
     * @param expiry The amount of time samples are accumulated to a histogram before it
     * is reset and rotated.
     * @return This builder.
     */
    public B distributionStatisticExpiry(@Nullable Duration expiry) {
        this.distributionConfigBuilder.expiry(expiry);
        return (B) this;
    }

    /**
     * Statistics emanating from a timer like max, percentiles, and histogram counts decay
     * over time to give greater weight to recent samples (exception: histogram counts are
     * cumulative for those systems that expect cumulative histogram buckets). Samples are
     * accumulated to such statistics in ring buffers which rotate after
     * {@link #distributionStatisticExpiry(Duration)}, with this buffer length.
     * @param bufferLength The number of histograms to keep in the ring buffer.
     * @return This builder.
     */
    public B distributionStatisticBufferLength(@Nullable Integer bufferLength) {
        this.distributionConfigBuilder.bufferLength(bufferLength);
        return (B) this;
    }

    /**
     * Sets the pause detector implementation to use for this timer. Can also be
     * configured on a registry-level with
     * {@link MeterRegistry.Config#pauseDetector(PauseDetector)}.
     * @param pauseDetector The pause detector implementation to use.
     * @return This builder.
     * @see io.micrometer.core.instrument.distribution.pause.NoPauseDetector
     * @see io.micrometer.core.instrument.distribution.pause.ClockDriftPauseDetector
     */
    public B pauseDetector(@Nullable PauseDetector pauseDetector) {
        this.pauseDetector = pauseDetector;
        return (B) this;
    }

    /**
     * @param description Description text of the eventual timer.
     * @return This builder.
     */
    public B description(@Nullable String description) {
        this.description = description;
        return (B) this;
    }

}
