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
import java.util.Arrays;
import java.util.Locale;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * A histogram implementation that does not support precomputed percentiles but supports
 * aggregable percentile histograms and SLA boundaries. There is no need for a high dynamic range
 * histogram and its more expensive memory footprint if all we are interested in is fixed histogram counts.
 *
 * @author Jon Schneider
 * @since 1.0.3
 */
public class TimeWindowFixedBoundaryHistogram
        extends AbstractTimeWindowHistogram<TimeWindowFixedBoundaryHistogram.FixedBoundaryHistogram, Void> {
    private final double[] buckets;

    private final boolean cumulativeBucketCounts;

    public TimeWindowFixedBoundaryHistogram(Clock clock, DistributionStatisticConfig config, boolean supportsAggregablePercentiles) {
        this(clock, config, supportsAggregablePercentiles, true);
    }

    /**
     * Create a {@code TimeWindowFixedBoundaryHistogram} instance.
     *
     * @param clock clock
     * @param config distribution statistic configuration
     * @param supportsAggregablePercentiles whether it supports aggregable percentiles
     * @param cumulativeBucketCounts whether it uses cumulative bucket counts
     * @since 1.9.0
     */
    public TimeWindowFixedBoundaryHistogram(Clock clock, DistributionStatisticConfig config, boolean supportsAggregablePercentiles,
                                            boolean cumulativeBucketCounts) {
        super(clock, config, FixedBoundaryHistogram.class, supportsAggregablePercentiles);

        this.cumulativeBucketCounts = cumulativeBucketCounts;

        NavigableSet<Double> histogramBuckets = distributionStatisticConfig.getHistogramBuckets(supportsAggregablePercentiles);

        Boolean percentileHistogram = distributionStatisticConfig.isPercentileHistogram();
        if (percentileHistogram != null && percentileHistogram) {
            histogramBuckets.addAll(PercentileHistogramBuckets.buckets(distributionStatisticConfig));
        }

        this.buckets = histogramBuckets.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).toArray();
        initRingBuffer();
    }

    @Override
    FixedBoundaryHistogram newBucket() {
        return new FixedBoundaryHistogram();
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
    double countAtValue(double value) {
        return this.cumulativeBucketCounts ? currentHistogram().countAtValueCumulative(value)
                : currentHistogram().countAtValue(value);
    }

    @Override
    void outputSummary(PrintStream printStream, double bucketScaling) {
        printStream.format("%14s %10s\n\n", "Bucket", "TotalCount");

        String bucketFormatString = "%14.1f %10d\n";

        for (int i = 0; i < buckets.length; i++) {
            printStream.format(Locale.US, bucketFormatString,
                    buckets[i] / bucketScaling,
                    currentHistogram().values.get(i));
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

    class FixedBoundaryHistogram {
        /**
         * For recording efficiency, this is a normal histogram. We turn these values into
         * cumulative counts only on calls to {@link #countAtValueCumulative(double)}.
         */
        final AtomicLongArray values;

        FixedBoundaryHistogram() {
            this.values = new AtomicLongArray(buckets.length);
        }

        long countAtValueCumulative(double value) {
            int index = Arrays.binarySearch(buckets, value);
            if (index < 0)
                return 0;
            long count = 0;
            for (int i = 0; i <= index; i++)
                count += values.get(i);
            return count;
        }

        long countAtValue(double value) {
            int index = Arrays.binarySearch(buckets, value);
            if (index < 0)
                return 0;
            return values.get(index);
        }

        void reset() {
            for (int i = 0; i < values.length(); i++) {
               values.set(i, 0);

            }
        }

        void record(long value) {
            int index = leastLessThanOrEqualTo(value);
            if (index > -1)
                values.incrementAndGet(index);
        }

        /**
         * The least bucket that is less than or equal to a sample.
         */
        int leastLessThanOrEqualTo(long key) {
            int low = 0;
            int high = buckets.length - 1;

            while (low <= high) {
                int mid = (low + high) >>> 1;
                if (buckets[mid] < key)
                    low = mid + 1;
                else if (buckets[mid] > key)
                    high = mid - 1;
                else
                    return mid; // exact match
            }

            return low < buckets.length ? low : -1;
        }
    }
}
