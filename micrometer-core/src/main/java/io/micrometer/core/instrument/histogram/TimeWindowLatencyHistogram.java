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
import io.micrometer.core.instrument.util.TimeUtils;
import org.HdrHistogram.Histogram;
import org.LatencyUtils.LatencyStats;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TimeWindowLatencyHistogram {
    private final Clock clock;
    private final StatsConfig config;
    private final LatencyStats[] ringBuffer;
    private int currentBucket;
    private long lastRotateTimestampMillis;
    private final long durationBetweenRotatesMillis;

    private final AtomicBoolean accumulatedHistogramStale = new AtomicBoolean(false);
    private final Histogram accumulatedHistogram;

    public TimeWindowLatencyHistogram(Clock clock, StatsConfig statsConfig) {
        this.clock = clock;
        this.config = statsConfig;
        int ageBuckets = statsConfig.getAgeBuckets();
        this.ringBuffer = new LatencyStats[ageBuckets];
        for (int i = 0; i < ageBuckets; i++) {
            this.ringBuffer[i] = buildLatencyStats();
        }
        this.currentBucket = 0;
        this.lastRotateTimestampMillis = clock.wallTime();
        this.durationBetweenRotatesMillis = statsConfig.getMaxAge().toMillis() / ageBuckets;
        this.accumulatedHistogram = new Histogram(ringBuffer[0].getIntervalHistogram());
    }

    public double percentile(double percentile, TimeUnit unit) {
        return TimeUtils.nanosToUnit(current().getValueAtPercentile(percentile * 100), unit);
    }

    public double histogramCountAtValue(long valueNanos) {
        return current().getCountBetweenValues(0, valueNanos);
    }

    public void record(long valueNano) {
        rotate();
        try {
            for (LatencyStats histogram : ringBuffer) {
                histogram.recordLatency(valueNano);
            }
            accumulatedHistogramStale.compareAndSet(false, true);
        } catch(ArrayIndexOutOfBoundsException ignored) {
            // the value is so large (or small) that the dynamic range of the histogram cannot be extended to include it
        }
    }

    private void rotate() {
        long timeSinceLastRotateMillis = clock.wallTime() - lastRotateTimestampMillis;

        while (timeSinceLastRotateMillis > durationBetweenRotatesMillis) {
            ringBuffer[currentBucket] = buildLatencyStats();
            accumulatedHistogram.reset();
            if (++currentBucket >= ringBuffer.length) {
                currentBucket = 0;
            }
            timeSinceLastRotateMillis -= durationBetweenRotatesMillis;
            lastRotateTimestampMillis += durationBetweenRotatesMillis;
            accumulatedHistogramStale.compareAndSet(false, true);
        }
    }

    private Histogram current() {
        rotate();

        if(accumulatedHistogramStale.compareAndSet(true, false)) {
            ringBuffer[currentBucket].addIntervalHistogramTo(accumulatedHistogram);
        }

        return accumulatedHistogram;
    }

    private LatencyStats buildLatencyStats() {
        return new LatencyStats.Builder()
            .lowestTrackableLatency(config.getMinimumExpectedValue())
            .highestTrackableLatency(config.getMaximumExpectedValue())
            .build();
    }
}
