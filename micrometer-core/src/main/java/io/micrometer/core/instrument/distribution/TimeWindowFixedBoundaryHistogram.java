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

import java.io.PrintStream;
import java.util.Locale;
import java.util.NavigableSet;
import java.util.Objects;

/**
 * A histogram implementation that does not support precomputed percentiles but supports
 * aggregable percentile histograms and SLO boundaries. There is no need for a high
 * dynamic range histogram and its more expensive memory footprint if all we are
 * interested in is fixed histogram counts.
 *
 * @author Jon Schneider
 * @since 1.0.3
 */
public class TimeWindowFixedBoundaryHistogram extends AbstractTimeWindowHistogram<FixedBoundaryHistogram, Void> {

    private final double[] buckets;

    private final boolean isCumulativeBucketCounts;

    /**
     * Create a {@code TimeWindowFixedBoundaryHistogram} instance with cumulative bucket
     * counts and buckets based on the {@link DistributionStatisticConfig config} and
     * {@code supportsAggregablePercentiles}.
     * @param clock clock
     * @param config distribution statistic configuration
     * @param supportsAggregablePercentiles whether the backend supports aggregable
     * percentiles
     * @see PercentileHistogramBuckets
     */
    public TimeWindowFixedBoundaryHistogram(Clock clock, DistributionStatisticConfig config,
            boolean supportsAggregablePercentiles) {
        this(clock, config, supportsAggregablePercentiles, true);
    }

    /**
     * Create a {@code TimeWindowFixedBoundaryHistogram} instance with buckets based on
     * the {@link DistributionStatisticConfig config} and
     * {@code supportsAggregablePercentiles}.
     * @param clock clock
     * @param config distribution statistic configuration
     * @param supportsAggregablePercentiles whether the backend supports aggregable
     * percentiles
     * @param isCumulativeBucketCounts whether to use cumulative bucket counts
     * @since 1.9.0
     * @see PercentileHistogramBuckets
     */
    public TimeWindowFixedBoundaryHistogram(Clock clock, DistributionStatisticConfig config,
            boolean supportsAggregablePercentiles, boolean isCumulativeBucketCounts) {
        this(clock, config, supportsAggregablePercentiles, isCumulativeBucketCounts, false);
    }

    /**
     * Create a {@code TimeWindowFixedBoundaryHistogram} instance with buckets based on
     * the {@link DistributionStatisticConfig config} and
     * {@code supportsAggregablePercentiles}. This constructor allows for use cases that
     * always need an infinity bucket in the histogram.
     * @param clock clock
     * @param config distribution statistic configuration
     * @param supportsAggregablePercentiles whether the backend supports aggregable
     * percentiles
     * @param isCumulativeBucketCounts whether to use cumulative bucket counts
     * @param includeInfinityBucket whether to always include an infinity bucket
     * @since 1.13.11
     * @see PercentileHistogramBuckets
     */
    protected TimeWindowFixedBoundaryHistogram(Clock clock, DistributionStatisticConfig config,
            boolean supportsAggregablePercentiles, boolean isCumulativeBucketCounts, boolean includeInfinityBucket) {
        super(clock, config, FixedBoundaryHistogram.class);

        this.isCumulativeBucketCounts = isCumulativeBucketCounts;

        NavigableSet<Double> histogramBuckets = distributionStatisticConfig
            .getHistogramBuckets(supportsAggregablePercentiles);

        if (includeInfinityBucket) {
            histogramBuckets.add(Double.POSITIVE_INFINITY);
        }

        this.buckets = histogramBuckets.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).toArray();
        initRingBuffer();
    }

    @Override
    FixedBoundaryHistogram newBucket() {
        return new FixedBoundaryHistogram(this.buckets, isCumulativeBucketCounts);
    }

    @Override
    void recordLong(FixedBoundaryHistogram bucket, long value) {
        bucket.record(value);
    }

    @Override
    final void recordDouble(FixedBoundaryHistogram bucket, double value) {
        recordLong(bucket, (long) Math.ceil(value));
    }

    @Override
    void resetBucket(FixedBoundaryHistogram bucket) {
        bucket.reset();
    }

    @Override
    Void newAccumulatedHistogram(FixedBoundaryHistogram[] ringBuffer) {
        return null;
    }

    @Override
    void accumulate() {
        // do nothing -- we aren't using swaps for source and accumulated
    }

    @Override
    void resetAccumulatedHistogram() {
    }

    @Override
    double valueAtPercentile(double percentile) {
        return 0;
    }

    @Override
    CountAtBucket[] countsAtBuckets() {
        return currentHistogram().getCountAtBuckets();
    }

    @Override
    void outputSummary(PrintStream printStream, double bucketScaling) {
        printStream.format("%14s %10s\n\n", "Bucket", "TotalCount");

        String bucketFormatString = "%14.1f %10d\n";

        FixedBoundaryHistogram currentHistogram = currentHistogram();
        for (int i = 0; i < buckets.length; i++) {
            printStream.format(Locale.US, bucketFormatString, buckets[i] / bucketScaling,
                    currentHistogram.values.get(i));
        }

        printStream.write('\n');
    }

    /**
     * Return buckets.
     * @return buckets
     * @since 1.9.0
     */
    protected double[] getBuckets() {
        return this.buckets;
    }

}
