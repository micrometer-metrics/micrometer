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
import io.micrometer.core.instrument.util.TimeUtils;
import org.HdrHistogram.Histogram;
import org.LatencyUtils.LatencyStats;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.ToDoubleFunction;

/**
 * @author Jon Schneider
 * @author Trustin Heuiseung Lee
 */
@Incubating(since = "1.0.0-rc.3")
public class TimeWindowLatencyHistogram {

    private static final AtomicIntegerFieldUpdater<TimeWindowLatencyHistogram> rotatingUpdater =
        AtomicIntegerFieldUpdater.newUpdater(TimeWindowLatencyHistogram.class, "rotating");

    private final Clock clock;
    private final HistogramConfig config;

    private final LatencyStats[] ringBuffer;
    private final Histogram accumulatedHistogram;
    private volatile boolean accumulatedHistogramStale;

    private final long durationBetweenRotatesMillis;
    private int currentBucket;
    private volatile long lastRotateTimestampMillis;
    @SuppressWarnings({ "unused", "FieldCanBeLocal" })
    private volatile int rotating; // 0 - not rotating, 1 - rotating

    public TimeWindowLatencyHistogram(Clock clock, HistogramConfig histogramConfig) {
        this.clock = clock;
        this.config = histogramConfig;
        int ageBuckets = histogramConfig.getHistogramBufferLength();
        this.ringBuffer = new LatencyStats[ageBuckets];
        for (int i = 0; i < ageBuckets; i++) {
            this.ringBuffer[i] = buildLatencyStats();
        }
        this.currentBucket = 0;
        this.lastRotateTimestampMillis = clock.wallTime();
        this.durationBetweenRotatesMillis = histogramConfig.getHistogramExpiry().toMillis() / ageBuckets;
        this.accumulatedHistogram = new Histogram(ringBuffer[0].getIntervalHistogram());
    }

    public double percentile(double percentile, TimeUnit unit) {
        return TimeUtils.nanosToUnit(get(h -> h.getValueAtPercentile(percentile * 100)), unit);
    }

    public double histogramCountAtValue(long valueNanos) {
        return get(h -> h.getCountBetweenValues(0, valueNanos));
    }

    public void record(long valueNano) {
        rotate();
        try {
            for (LatencyStats histogram : ringBuffer) {
                histogram.recordLatency(valueNano);
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
                    ringBuffer[currentBucket] = buildLatencyStats();
                    if (++currentBucket >= ringBuffer.length) {
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

    private double get(ToDoubleFunction<Histogram> func) {
        rotate();

        synchronized (this) {
            if (accumulatedHistogramStale) {
                ringBuffer[currentBucket].addIntervalHistogramTo(accumulatedHistogram);
                accumulatedHistogramStale = false;
            }

            return func.applyAsDouble(accumulatedHistogram);
        }
    }

    private LatencyStats buildLatencyStats() {
        return new LatencyStats.Builder()
            .lowestTrackableLatency(config.getMinimumExpectedValue())
            .highestTrackableLatency(config.getMaximumExpectedValue())
            .build();
    }
}
