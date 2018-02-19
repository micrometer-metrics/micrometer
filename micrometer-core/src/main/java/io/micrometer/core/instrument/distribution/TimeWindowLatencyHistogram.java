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
package io.micrometer.core.instrument.distribution;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.pause.ClockDriftPauseDetector;
import io.micrometer.core.instrument.distribution.pause.NoPauseDetector;
import org.HdrHistogram.Histogram;
import org.LatencyUtils.LatencyStats;
import org.LatencyUtils.PauseDetector;
import org.LatencyUtils.SimplePauseDetector;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * @author Jon Schneider
 * @author Trustin Heuiseung Lee
 */
@Incubating(since = "1.0.0-rc.3")
public class TimeWindowLatencyHistogram extends TimeWindowHistogramBase<LatencyStats, Histogram> implements AutoCloseable {
    private static Map<io.micrometer.core.instrument.distribution.pause.PauseDetector, PauseDetector> pauseDetectorCache =
        new ConcurrentHashMap<>();

    private final PauseDetector pauseDetector;

    /*VisibleForTesting*/
    public TimeWindowLatencyHistogram(Clock clock, DistributionStatisticConfig distributionStatisticConfig) {
        this(clock, distributionStatisticConfig, new ClockDriftPauseDetector(Duration.ofMillis(100), Duration.ofMillis(100)));
    }

    public TimeWindowLatencyHistogram(Clock clock, DistributionStatisticConfig distributionStatisticConfig,
                                      io.micrometer.core.instrument.distribution.pause.PauseDetector pauseDetector) {
        super(clock, distributionStatisticConfig, LatencyStats.class);

        this.pauseDetector = requireNonNull(pauseDetectorCache.computeIfAbsent(pauseDetector, detector -> {
            if (detector instanceof ClockDriftPauseDetector) {
                ClockDriftPauseDetector clockDriftPauseDetector = (ClockDriftPauseDetector) detector;
                return new SimplePauseDetector(clockDriftPauseDetector.getSleepInterval().toNanos(),
                    clockDriftPauseDetector.getPauseThreshold().toNanos(), 1, false);
            } else if (detector instanceof NoPauseDetector) {
                return new NoopPauseDetector();
            }
            return new NoopPauseDetector();
        }));

        initRingBuffer();
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    LatencyStats newBucket(DistributionStatisticConfig distributionStatisticConfig) {
        return new LatencyStats.Builder()
            .pauseDetector(pauseDetector)
            .lowestTrackableLatency(distributionStatisticConfig.getMinimumExpectedValue())
            .highestTrackableLatency(distributionStatisticConfig.getMaximumExpectedValue())
            .numberOfSignificantValueDigits(NUM_SIGNIFICANT_VALUE_DIGITS)
            .build();
    }

    @Override
    void recordLong(LatencyStats bucket, long value) {
        bucket.recordLatency(value);
    }

    @Override
    void recordDouble(LatencyStats bucket, double value) {
        bucket.recordLatency((long) value);
    }

    @Override
    void resetBucket(LatencyStats bucket) {
        // LatencyStats does not provide a way to reset the counters, so we just drain into a NoopHistogram.
        bucket.getIntervalHistogramInto(NoopHistogram.INSTANCE);
    }

    @Override
    Histogram newAccumulatedHistogram(LatencyStats[] ringBuffer) {
        return ringBuffer[0].getIntervalHistogram();
    }

    @Override
    void accumulate(LatencyStats sourceBucket, Histogram accumulatedHistogram) {
        sourceBucket.addIntervalHistogramTo(accumulatedHistogram);
    }

    @Override
    void resetAccumulatedHistogram(Histogram accumulatedHistogram) {
        accumulatedHistogram.reset();
    }

    @Override
    double valueAtPercentile(Histogram accumulatedHistogram, double percentile) {
        return accumulatedHistogram.getValueAtPercentile(percentile);
    }

    @Override
    double countAtValue(Histogram accumulatedHistogram, long value) {
        return accumulatedHistogram.getCountBetweenValues(0, value);
    }

    @Override
    public void close() {
        pauseDetector.shutdown();
    }

    private static class NoopPauseDetector extends PauseDetector {
        NoopPauseDetector() {
            shutdown();
        }
    }

    // VisibleForTesting
    PauseDetector getPauseDetector() {
        return pauseDetector;
    }
}
