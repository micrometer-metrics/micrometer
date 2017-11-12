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

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.Clock;
import org.HdrHistogram.DoubleHistogram;
import org.HdrHistogram.DoubleRecorder;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.ToDoubleFunction;

/**
 * @author Jon Schneider
 * @author Trustin Heuiseung Lee
 */
@Incubating(since = "1.0.0-rc.3")
public class TimeWindowHistogram {

    private static final AtomicIntegerFieldUpdater<TimeWindowHistogram> rotatingUpdater =
        AtomicIntegerFieldUpdater.newUpdater(TimeWindowHistogram.class, "rotating");

    private final Clock clock;

    private final DoubleRecorder[] recorderRingBuffer;
    private final DoubleHistogram intervalHistogram;
    private final DoubleHistogram accumulatedHistogram;
    private volatile boolean accumulatedHistogramStale;

    private final long durationBetweenRotatesMillis;
    private int currentBucket;
    private volatile long lastRotateTimestampMillis;
    @SuppressWarnings({ "unused", "FieldCanBeLocal" })
    private volatile int rotating; // 0 - not rotating, 1 - rotating

    public TimeWindowHistogram(Clock clock, HistogramConfig histogramConfig) {
        this.clock = clock;
        int ageBuckets = histogramConfig.getHistogramBufferLength();
        this.recorderRingBuffer = new DoubleRecorder[ageBuckets];
        this.intervalHistogram = new DoubleHistogram(3);
        this.accumulatedHistogram = new DoubleHistogram(3);
        for (int i = 0; i < ageBuckets; i++) {
            this.recorderRingBuffer[i] = new DoubleRecorder(3);
        }
        this.currentBucket = 0;
        this.lastRotateTimestampMillis = clock.wallTime();
        this.durationBetweenRotatesMillis = histogramConfig.getHistogramExpiry().toMillis() / ageBuckets;
    }

    public double percentile(double percentile) {
        return get(h -> h.getValueAtPercentile(percentile * 100));
    }

    public double histogramCountAtValue(double value) {
        return get(h -> h.getCountBetweenValues(0, value));
    }

    public void record(double value) {
        rotate();
        try {
            for (DoubleRecorder recorder : recorderRingBuffer) {
                recorder.recordValue(value);
            }
        } catch (IndexOutOfBoundsException ignored) {
            // the value is so large (or small) that the dynamic range of the histogram cannot be extended to include it
        } finally {
            accumulatedHistogramStale = true;
        }
    }

    private void rotate() {
        long timeSinceLastRotateMillis = clock.wallTime() - lastRotateTimestampMillis;
        if (timeSinceLastRotateMillis <= durationBetweenRotatesMillis) {
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
                    recorderRingBuffer[currentBucket].reset();
                    if (++currentBucket >= recorderRingBuffer.length) {
                        currentBucket = 0;
                    }
                    timeSinceLastRotateMillis -= durationBetweenRotatesMillis;
                    lastRotateTimestampMillis += durationBetweenRotatesMillis;
                } while (timeSinceLastRotateMillis > durationBetweenRotatesMillis);

                accumulatedHistogram.reset();
                accumulatedHistogramStale = true;
            }
        } finally {
            rotating = 0;
        }
    }

    private double get(ToDoubleFunction<DoubleHistogram> func) {
        rotate();

        synchronized (this) {
            if (accumulatedHistogramStale) {
                recorderRingBuffer[currentBucket].getIntervalHistogramInto(intervalHistogram);
                accumulatedHistogram.add(intervalHistogram);
                accumulatedHistogramStale = false;
            }

            return func.applyAsDouble(accumulatedHistogram);
        }
    }
}
