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
package io.micrometer.core.instrument.histogram;

import io.micrometer.core.instrument.Clock;
import java.lang.reflect.Array;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.ToDoubleFunction;

/**
 * @param <T> the type of the buckets in a ring buffer
 * @param <U> the type of accumulated histogram
 *
 * @author Jon Schneider
 * @author Trustin Heuiseung Lee
 */
abstract class TimeWindowHistogramBase<T, U> {

    static final int NUM_SIGNIFICANT_VALUE_DIGITS = 2;

    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<TimeWindowHistogramBase> rotatingUpdater =
        AtomicIntegerFieldUpdater.newUpdater(TimeWindowHistogramBase.class, "rotating");

    private final Clock clock;

    private final T[] ringBuffer;
    private final U accumulatedHistogram;
    private volatile boolean accumulatedHistogramStale;

    private final long durationBetweenRotatesMillis;
    private int currentBucket;
    private volatile long lastRotateTimestampMillis;
    @SuppressWarnings({ "unused", "FieldCanBeLocal" })
    private volatile int rotating; // 0 - not rotating, 1 - rotating

    TimeWindowHistogramBase(Clock clock, HistogramConfig histogramConfig, Class<T> bucketType) {
        this.clock = clock;

        final int ageBuckets = histogramConfig.getHistogramBufferLength();
        ringBuffer = newRingBuffer(bucketType, ageBuckets, histogramConfig);
        accumulatedHistogram = newAccumulatedHistogram(ringBuffer);

        durationBetweenRotatesMillis = histogramConfig.getHistogramExpiry().toMillis() / ageBuckets;
        currentBucket = 0;
        lastRotateTimestampMillis = clock.wallTime();
    }

    private T[] newRingBuffer(Class<T> bucketType, int ageBuckets, HistogramConfig histogramConfig) {
        @SuppressWarnings("unchecked")
        final T[] ringBuffer = (T[]) Array.newInstance(bucketType, ageBuckets);
        for (int i = 0; i < ageBuckets; i++) {
            ringBuffer[i] = newBucket(histogramConfig);
        }
        return ringBuffer;
    }

    abstract T newBucket(HistogramConfig histogramConfig);
    abstract void recordLong(T bucket, long value);
    abstract void recordDouble(T bucket, double value);
    abstract void resetBucket(T bucket);

    abstract U newAccumulatedHistogram(T[] ringBuffer);
    abstract void accumulate(T sourceBucket, U accumulatedHistogram);
    abstract void resetAccumulatedHistogram(U accumulatedHistogram);

    abstract double valueAtPercentile(U accumulatedHistogram, double percentile);
    abstract double countAtValue(U accumulatedHistogram, long value);

    public final double percentile(double percentile) {
        return get(h -> valueAtPercentile(h, percentile * 100));
    }

    public final double histogramCountAtValue(long value) {
        return get(h -> countAtValue(h, value));
    }

    public final void recordLong(long value) {
        rotate();
        try {
            for (T bucket : ringBuffer) {
                recordLong(bucket, value);
            }
        } catch (IndexOutOfBoundsException ignored) {
            // the value is so large (or small) that the dynamic range of the histogram cannot be extended to include it
        } finally {
            accumulatedHistogramStale = true;
        }
    }

    public final void recordDouble(double value) {
        rotate();
        try {
            for (T bucket : ringBuffer) {
                recordDouble(bucket, value);
            }
        } catch (IndexOutOfBoundsException ignored) {
            // the value is so large (or small) that the dynamic range of the histogram cannot be extended to include it
        } finally {
            accumulatedHistogramStale = true;
        }
    }

    private void rotate() {
        long timeSinceLastRotateMillis = clock.wallTime() - lastRotateTimestampMillis;
        if (timeSinceLastRotateMillis < durationBetweenRotatesMillis) {
            // Need to wait more for next rotation.
            return;
        }

        if (!rotatingUpdater.compareAndSet(this, 0, 1)) {
            // Being rotated by other thread already.
            return;
        }

        try {
            synchronized (this) {
                do {
                    resetBucket(ringBuffer[currentBucket]);
                    if (++currentBucket >= ringBuffer.length) {
                        currentBucket = 0;
                    }
                    timeSinceLastRotateMillis -= durationBetweenRotatesMillis;
                    lastRotateTimestampMillis += durationBetweenRotatesMillis;
                } while (timeSinceLastRotateMillis >= durationBetweenRotatesMillis);

                resetAccumulatedHistogram(accumulatedHistogram);
                accumulatedHistogramStale = true;
            }
        } finally {
            rotating = 0;
        }
    }

    private double get(ToDoubleFunction<U> func) {
        rotate();

        synchronized (this) {
            if (accumulatedHistogramStale) {
                accumulate(ringBuffer[currentBucket], accumulatedHistogram);
                accumulatedHistogramStale = false;
            }

            return func.applyAsDouble(accumulatedHistogram);
        }
    }
}
