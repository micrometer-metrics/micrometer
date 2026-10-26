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

import io.micrometer.core.instrument.Clock;

/**
 * <b>NOTE: This class is intended for internal use as an implementation detail. You
 * should not compile against its API. Please contact the project maintainers if you need
 * this as public API.</b>
 * <p>
 * A histogram implementation that supports the computation of percentiles by Micrometer
 * for publishing to a monitoring system.
 *
 * @author Jon Schneider
 * @author Trustin Heuiseung Lee
 */
public class TimeWindowPercentileHistogram implements Histogram {

    // HdrHistogram is a runtime-only dependency of micrometer-core, so its types must
    // not appear in the signature (including supertypes) of this class.
    private final HdrTimeWindowHistogram delegate;

    public TimeWindowPercentileHistogram(Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            boolean supportsAggregablePercentiles) {
        this(clock, distributionStatisticConfig, supportsAggregablePercentiles, true, false);
    }

    /**
     * This constructor allows full customization of the histogram characteristics.
     * @param clock clock used for time windowing
     * @param distributionStatisticConfig distribution config to use with this histogram
     * @param supportsAggregablePercentiles whether the backend receiving this histogram
     * supports aggregating histograms to estimate percentiles
     * @param isCumulativeBucketCounts whether histogram bucket counts are cumulative
     * @param includeInfinityBucket whether to include the infinity histogram bucket
     * @since 1.13.11
     */
    protected TimeWindowPercentileHistogram(Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            boolean supportsAggregablePercentiles, boolean isCumulativeBucketCounts, boolean includeInfinityBucket) {
        this.delegate = new HdrTimeWindowHistogram(clock, distributionStatisticConfig, supportsAggregablePercentiles,
                isCumulativeBucketCounts, includeInfinityBucket);
    }

    @Override
    public void recordLong(long value) {
        delegate.recordLong(value);
    }

    @Override
    public void recordDouble(double value) {
        delegate.recordDouble(value);
    }

    @Override
    public final HistogramSnapshot takeSnapshot(long count, double total, double max) {
        return delegate.takeSnapshot(count, total, max);
    }

}
