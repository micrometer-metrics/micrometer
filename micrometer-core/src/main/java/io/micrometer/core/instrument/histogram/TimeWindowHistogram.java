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
import org.HdrHistogram.DoubleHistogram;
import org.HdrHistogram.DoubleRecorder;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Jon Schneider
 */
public class TimeWindowHistogram {
    private final Clock clock;

    private final DoubleRecorder[] recorderRingBuffer;
    private final DoubleHistogram intervalHistogram;
    private final DoubleHistogram accumulatedHistogram;
    private final AtomicBoolean accumulatedHistogramStale = new AtomicBoolean(false);

    private int currentBucket;
    private long lastRotateTimestampMillis;
    private final long durationBetweenRotatesMillis;

    public TimeWindowHistogram(Clock clock, StatsConfig statsConfig) {
        this.clock = clock;
        int ageBuckets = statsConfig.getAgeBuckets();
        this.recorderRingBuffer = new DoubleRecorder[ageBuckets];
        this.intervalHistogram = new DoubleHistogram(3);
        this.accumulatedHistogram = new DoubleHistogram(3);
        for (int i = 0; i < ageBuckets; i++) {
            this.recorderRingBuffer[i] = new DoubleRecorder(3);
        }
        this.currentBucket = 0;
        this.lastRotateTimestampMillis = clock.wallTime();
        this.durationBetweenRotatesMillis = statsConfig.getMaxAge().toMillis() / ageBuckets;
    }

    public double percentile(double percentile) {
        return current().getValueAtPercentile(percentile * 100);
    }

    public double histogramCountAtValue(double value) {
        return current().getCountBetweenValues(0, value);
    }

    public void record(double value) {
        rotate();
        try {
            for (DoubleRecorder recorder : recorderRingBuffer) {
                recorder.recordValue(value);
            }
            accumulatedHistogramStale.compareAndSet(false, true);
        } catch(ArrayIndexOutOfBoundsException ignored) {
            // the value is so large (or small) that the dynamic range of the histogram cannot be extended to include it
        }
    }

    private void rotate() {
        long timeSinceLastRotateMillis = clock.wallTime() - lastRotateTimestampMillis;
        while (timeSinceLastRotateMillis > durationBetweenRotatesMillis) {
            recorderRingBuffer[currentBucket].reset();
            accumulatedHistogram.reset();
            if (++currentBucket >= recorderRingBuffer.length) {
                currentBucket = 0;
            }
            timeSinceLastRotateMillis -= durationBetweenRotatesMillis;
            lastRotateTimestampMillis += durationBetweenRotatesMillis;
            accumulatedHistogramStale.compareAndSet(false, true);
        }
    }

    private DoubleHistogram current() {
        rotate();

        if(accumulatedHistogramStale.compareAndSet(true, false)) {
            recorderRingBuffer[currentBucket].getIntervalHistogramInto(intervalHistogram);
            accumulatedHistogram.add(intervalHistogram);
        }

        return accumulatedHistogram;
    }
}
