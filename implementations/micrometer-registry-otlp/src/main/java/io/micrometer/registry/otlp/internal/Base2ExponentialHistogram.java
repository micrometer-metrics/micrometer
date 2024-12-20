/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.registry.otlp.internal;

import static io.micrometer.registry.otlp.internal.ExponentialHistogramSnapShot.ExponentialBuckets.EMPTY_EXPONENTIAL_BUCKET;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.distribution.Histogram;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.registry.otlp.internal.ExponentialHistogramSnapShot.ExponentialBuckets;

/**
 * A ExponentialHistogram implementation that compresses bucket boundaries using an
 * exponential formula (Base2 exponent), making it suitable for conveying high dynamic
 * range data with small relative error. This is an implementation of the <a href=
 * "https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/data-model.md#exponentialhistogram">Exponential
 * Histogram</a> as per the OTLP specification. The internal implementations uses the
 * techniques outlined in the OTLP specification mentioned above. This implementation
 * supports only recording positive values (enforced by
 * {@link io.micrometer.core.instrument.AbstractTimer#record(long, TimeUnit)}).
 * <p>
 * <strong> This is an internal class and might have breaking changes, external
 * implementations SHOULD NOT rely on this implementation. </strong>
 * </p>
 *
 * @author Lenin Jaganathan
 * @since 1.14.0
 */
public abstract class Base2ExponentialHistogram implements Histogram {

    private final int maxScale;

    private final int maxBucketsCount;

    private final double zeroThreshold;

    @Nullable
    private final TimeUnit baseUnit;

    private final LongAdder zeroCount = new LongAdder();

    private CircularCountHolder circularCountHolder;

    private IndexProvider base2IndexProvider;

    private int scale;

    /**
     * Creates an Base2ExponentialHistogram that records positive values.
     * @param maxScale - maximum scale that can be used. The recordings start with this
     * scale and gets downscaled to the scale that supports recording data within
     * maxBucketsCount.
     * @param maxBucketsCount - maximum number of buckets that can be used for
     * distribution.
     * @param minimumExpectedValue - values less than this are considered in zero count
     * and not recorded in the histogram. If less than 0, this is rounded to zero. In case
     * of recording time, this should be in nanoseconds.
     * @param baseUnit - an Optional TimeUnit. If set to a non-null unit, the recorded
     * values are converted to this unit.
     */
    Base2ExponentialHistogram(int maxScale, int maxBucketsCount, double minimumExpectedValue,
            @Nullable TimeUnit baseUnit) {
        this.maxScale = maxScale;
        this.scale = maxScale;
        this.maxBucketsCount = maxBucketsCount;
        this.baseUnit = baseUnit;
        this.zeroThreshold = getZeroThreshHoldFromMinExpectedValue(minimumExpectedValue, baseUnit);

        this.circularCountHolder = new CircularCountHolder(maxBucketsCount);
        this.base2IndexProvider = IndexProviderFactory.getIndexProviderForScale(scale);
    }

    /**
     * Convert the minimumExpectedValue to zeroThreshold. Micrometer's
     * minimumExpectedValue should be included as part of distribution whereas Exponential
     * Histogram will exclude the zeroThreshold from distribution. Hence, we find the next
     * smallest value from minimumExpectedValue and use it as zeroThreshold.
     */
    private static double getZeroThreshHoldFromMinExpectedValue(final double minimumExpectedValue,
            final @Nullable TimeUnit baseUnit) {
        double minValueScaledToTime = baseUnit != null ? TimeUtils.nanosToUnit(minimumExpectedValue, baseUnit)
                : minimumExpectedValue;
        return Math.max(Math.nextDown(minValueScaledToTime), 0.0);
    }

    /**
     * Returns the latest snapshot of recordings from
     * {@link Base2ExponentialHistogram#takeExponentialHistogramSnapShot()} and not the
     * current set of values. It is recommended to use this method to consume values
     * recorded in this Histogram as this will provide consistency in recorded values.
     */
    public abstract ExponentialHistogramSnapShot getLatestExponentialHistogramSnapshot();

    /**
     * Takes a snapshot of the values that are recorded.
     */
    abstract void takeExponentialHistogramSnapShot();

    int getScale() {
        return scale;
    }

    /**
     * Provides a bridge to Micrometer {@link HistogramSnapshot}.
     */
    @Override
    public HistogramSnapshot takeSnapshot(final long count, final double total, final double max) {
        this.takeExponentialHistogramSnapShot();
        return new HistogramSnapshot(count, total, max, null, null, null);
    }

    /**
     * Returns the snapshot of current recorded values.
     */
    ExponentialHistogramSnapShot getCurrentValuesSnapshot() {
        return (circularCountHolder.isEmpty() && zeroCount.longValue() == 0)
                ? DefaultExponentialHistogramSnapShot.getEmptySnapshotForScale(scale)
                : new DefaultExponentialHistogramSnapShot(scale, zeroCount.longValue(), zeroThreshold,
                        new ExponentialBuckets(getOffset(), getBucketCounts()), EMPTY_EXPONENTIAL_BUCKET);
    }

    /**
     * Records the value to the Histogram. While measuring time, this value will be
     * converted to {@link Base2ExponentialHistogram#baseUnit}.
     * @param value - value to be recorded in the Histogram. (in
     * {@link TimeUnit#NANOSECONDS} if recording time.)
     */
    @Override
    public void recordLong(final long value) {
        recordDouble(value);
    }

    /**
     * Records the value to the Histogram. While measuring time, this value will be
     * converted {@link Base2ExponentialHistogram#baseUnit}.
     * @param value - value to be recorded in the Histogram. (in
     * {@link TimeUnit#NANOSECONDS} if recording time.)
     */
    @Override
    public void recordDouble(double value) {
        if (baseUnit != null) {
            value = TimeUtils.nanosToUnit(value, baseUnit);
        }

        if (value <= zeroThreshold) {
            zeroCount.increment();
            return;
        }
        recordToHistogram(value);
    }

    private synchronized void recordToHistogram(final double value) {
        int index = base2IndexProvider.getIndexForValue(value);
        if (!circularCountHolder.increment(index, 1)) {
            int downScaleFactor = getDownScaleFactor(index);
            downScale(downScaleFactor);
            index = base2IndexProvider.getIndexForValue(value);
            circularCountHolder.increment(index, 1);
        }
    }

    /**
     * Reduces the scale of the histogram by downScaleFactor. The buckets are merged to
     * align with the exponential scale.
     * @param downScaleFactor - the factor to downscale this histogram.
     */
    private void downScale(int downScaleFactor) {
        if (downScaleFactor == 0) {
            return;
        }

        if (!circularCountHolder.isEmpty()) {
            CircularCountHolder newCounts = new CircularCountHolder(maxBucketsCount);

            for (int i = circularCountHolder.getStartIndex(); i <= circularCountHolder.getEndIndex(); i++) {
                long count = circularCountHolder.getValueAtIndex(i);
                if (count > 0) {
                    newCounts.increment(i >> downScaleFactor, count);
                }
            }
            this.circularCountHolder = newCounts;
        }

        this.updateScale(this.scale - downScaleFactor);
    }

    private void updateScale(int newScale) {
        if (newScale > maxScale) {
            newScale = maxScale;
        }
        this.scale = newScale;
        this.base2IndexProvider = IndexProviderFactory.getIndexProviderForScale(scale);
    }

    /**
     * Provide a downscale factor for the {@link Base2ExponentialHistogram} so that the
     * value can be recorded within {@link Base2ExponentialHistogram#maxBucketsCount}.
     * @param index - the index to which current value belongs to.
     * @return a factor by which {@link Base2ExponentialHistogram#scale} should be
     * decreased.
     */
    private int getDownScaleFactor(final long index) {
        long newStart = Math.min(index, circularCountHolder.getStartIndex());
        long newEnd = Math.max(index, circularCountHolder.getEndIndex());

        int scaleDownFactor = 0;
        while (newEnd - newStart + 1 > maxBucketsCount) {
            newStart >>= 1;
            newEnd >>= 1;
            scaleDownFactor++;
        }
        return scaleDownFactor;
    }

    /**
     * Provides a factor by which {@link Base2ExponentialHistogram#scale} can be increased
     * so that the values can still be represented using
     * {@link Base2ExponentialHistogram#maxBucketsCount}. This does not reset the last
     * used scale but makes the best attempt based on data recorded for last interval. In
     * most cases the range of values recorded within an {@link Base2ExponentialHistogram}
     * instance stays same, and we should avoid re-scaling to minimize garbage creation.
     * This applies only for
     * {@link io.micrometer.registry.otlp.AggregationTemporality#DELTA} where values are
     * reset for every interval.
     * @return - a factor by which the {@link Base2ExponentialHistogram#scale} should be
     * increased.
     */
    private int getUpscaleFactor() {
        if (!circularCountHolder.isEmpty()) {
            int indexDelta = circularCountHolder.getEndIndex() - circularCountHolder.getStartIndex() + 1;
            if (indexDelta == 1) {
                return maxScale - scale;
            }
            return (int) Math.floor(Math.log(maxBucketsCount / (double) indexDelta) / Math.log(2));
        }
        // When there are no recordings we will fall back to max scale.
        return maxScale - scale;
    }

    private int getOffset() {
        if (circularCountHolder.isEmpty()) {
            return 0;
        }
        return circularCountHolder.getStartIndex();
    }

    /**
     * Returns the list of buckets representing the values recorded. This is always less
     * than or equal to {@link Base2ExponentialHistogram#maxBucketsCount}.
     */
    private List<Long> getBucketCounts() {
        if (circularCountHolder.isEmpty()) {
            return Collections.emptyList();
        }

        int length = circularCountHolder.getEndIndex() - circularCountHolder.getStartIndex() + 1;

        long[] countsArr = new long[length];
        for (int i = 0; i < length; i++) {
            countsArr[i] = circularCountHolder.getValueAtIndex(i + circularCountHolder.getStartIndex());
        }
        return Arrays.stream(countsArr).boxed().collect(Collectors.toList());
    }

    /**
     * Reset the current values and possibly increase the scale based on current recorded
     * values;
     */
    synchronized void reset() {
        int upscaleFactor = getUpscaleFactor();
        if (upscaleFactor > 0) {
            this.updateScale(this.scale + upscaleFactor);
        }

        this.circularCountHolder.reset();
        this.zeroCount.reset();
    }

}
