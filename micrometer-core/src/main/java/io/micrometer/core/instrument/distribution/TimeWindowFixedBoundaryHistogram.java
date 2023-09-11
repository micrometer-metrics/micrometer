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
import java.util.Iterator;
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

    public TimeWindowFixedBoundaryHistogram(Clock clock, DistributionStatisticConfig config,
            boolean supportsAggregablePercentiles) {
        this(clock, config, supportsAggregablePercentiles, true);
    }

    /**
     * Create a {@code TimeWindowFixedBoundaryHistogram} instance.
     * @param clock clock
     * @param config distribution statistic configuration
     * @param supportsAggregablePercentiles whether it supports aggregable percentiles
     * @param isCumulativeBucketCounts whether it uses cumulative bucket counts
     * @since 1.9.0
     */
    public TimeWindowFixedBoundaryHistogram(Clock clock, DistributionStatisticConfig config,
            boolean supportsAggregablePercentiles, boolean isCumulativeBucketCounts) {
        super(clock, config, FixedBoundaryHistogram.class, supportsAggregablePercentiles);

        this.isCumulativeBucketCounts = isCumulativeBucketCounts;

        NavigableSet<Double> histogramBuckets = distributionStatisticConfig
            .getHistogramBuckets(supportsAggregablePercentiles);

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

    /**
     * For recording efficiency, we turn normal histogram into cumulative count histogram
     * only on calls to {@link FixedBoundaryHistogram#countsAtValues(Iterator)}.
     */
    @Override
    Iterator<CountAtBucket> countsAtValues(Iterator<Double> values) {
        return currentHistogram().countsAtValues(values);
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
