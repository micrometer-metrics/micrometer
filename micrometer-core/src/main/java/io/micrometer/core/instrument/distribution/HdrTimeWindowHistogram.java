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

import io.micrometer.common.util.internal.logging.WarnThenDebugLogger;
import io.micrometer.core.instrument.Clock;
import org.HdrHistogram.DoubleHistogram;
import org.HdrHistogram.DoubleRecorder;

import java.io.PrintStream;
import java.util.Objects;
import java.util.Set;

/**
 * HdrHistogram-backed implementation of {@link AbstractTimeWindowHistogram}. Kept
 * separate from {@link TimeWindowPercentileHistogram} so HdrHistogram types do not appear
 * in the signature of public classes, as HdrHistogram is not on the compile classpath of
 * consumers of micrometer-core.
 *
 * @author Jon Schneider
 * @author Trustin Heuiseung Lee
 */
final class HdrTimeWindowHistogram extends AbstractTimeWindowHistogram<DoubleRecorder, DoubleHistogram> {

    private static final WarnThenDebugLogger WARN_THEN_DEBUG_LOGGER = new WarnThenDebugLogger(
            HdrTimeWindowHistogram.class);

    private final DoubleHistogram intervalHistogram;

    private final double[] histogramBuckets;

    private final boolean isCumulativeBucketCounts;

    HdrTimeWindowHistogram(Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            boolean supportsAggregablePercentiles, boolean isCumulativeBucketCounts, boolean includeInfinityBucket) {
        super(clock, distributionStatisticConfig, DoubleRecorder.class);
        intervalHistogram = new DoubleHistogram(percentilePrecision(distributionStatisticConfig));
        this.isCumulativeBucketCounts = isCumulativeBucketCounts;

        Set<Double> monitoredBuckets = distributionStatisticConfig.getHistogramBuckets(supportsAggregablePercentiles);
        if (includeInfinityBucket) {
            monitoredBuckets.add(Double.POSITIVE_INFINITY);
        }
        histogramBuckets = monitoredBuckets.stream()
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .toArray();
        initRingBuffer();
    }

    @Override
    DoubleRecorder newBucket() {
        return new DoubleRecorder(percentilePrecision(distributionStatisticConfig));
    }

    @Override
    void recordDouble(DoubleRecorder bucket, double value) {
        bucket.recordValue(value);
    }

    @Override
    void recordLong(DoubleRecorder bucket, long value) {
        bucket.recordValue((double) value);
    }

    @Override
    void resetBucket(DoubleRecorder bucket) {
        bucket.reset();
    }

    @Override
    DoubleHistogram newAccumulatedHistogram(DoubleRecorder[] ringBuffer) {
        return new DoubleHistogram(percentilePrecision(distributionStatisticConfig));
    }

    @Override
    void accumulate() {
        currentHistogram().getIntervalHistogramInto(intervalHistogram);
        try {
            accumulatedHistogram().add(intervalHistogram);
        }
        catch (ArrayIndexOutOfBoundsException ex) {
            WARN_THEN_DEBUG_LOGGER.log("Failed to accumulate.", ex);
        }
    }

    @Override
    void resetAccumulatedHistogram() {
        accumulatedHistogram().reset();
    }

    @Override
    double valueAtPercentile(double percentile) {
        return accumulatedHistogram().getValueAtPercentile(percentile);
    }

    @Override
    CountAtBucket[] countsAtBuckets() {
        double cumulativeCount = 0.0;
        double lowerBoundValue = 0.0;
        CountAtBucket[] counts = new CountAtBucket[histogramBuckets.length];
        for (int i = 0; i < counts.length; i++) {
            double higherBoundValue = histogramBuckets[i];
            double count = accumulatedHistogram().getCountBetweenValues(lowerBoundValue, higherBoundValue);
            lowerBoundValue = accumulatedHistogram().nextNonEquivalentValue(higherBoundValue);
            counts[i] = new CountAtBucket(higherBoundValue,
                    isCumulativeBucketCounts ? cumulativeCount += count : count);
        }
        return counts;
    }

    private int percentilePrecision(DistributionStatisticConfig config) {
        return config.getPercentilePrecision() == null ? 1 : config.getPercentilePrecision();
    }

    @Override
    void outputSummary(PrintStream out, double bucketScaling) {
        accumulatedHistogram().outputPercentileDistribution(out, bucketScaling);
    }

}
