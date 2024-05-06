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
package io.micrometer.core.instrument;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.WarnThenDebugLogger;
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.core.instrument.distribution.pause.ClockDriftPauseDetector;
import io.micrometer.core.instrument.distribution.pause.NoPauseDetector;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import org.LatencyUtils.IntervalEstimator;
import org.LatencyUtils.SimplePauseDetector;
import org.LatencyUtils.TimeCappedMovingAverageIntervalEstimator;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public abstract class AbstractTimer extends AbstractMeter implements Timer {

    private static final WarnThenDebugLogger log = new WarnThenDebugLogger(AbstractTimer.class);

    private static final Map<PauseDetector, Object> pauseDetectorCache = new ConcurrentHashMap<>();

    protected final Clock clock;

    protected final Histogram histogram;

    private final TimeUnit baseTimeUnit;

    // Only used when pause detection is enabled
    @Nullable
    private Object intervalEstimator;

    @Nullable
    private org.LatencyUtils.PauseDetector pauseDetector;

    /**
     * Creates a new timer.
     * @param id The timer's name and tags.
     * @param clock The clock used to measure latency.
     * @param distributionStatisticConfig Configuration determining which distribution
     * statistics are sent.
     * @param pauseDetector Compensation for coordinated omission.
     * @param baseTimeUnit The time scale of this timer.
     * @deprecated Timer implementations should now declare at construction time whether
     * they support aggregable percentiles or not. By declaring it up front, Micrometer
     * can memory optimize the histogram structure used to store distribution statistics.
     */
    @Deprecated
    protected AbstractTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector, TimeUnit baseTimeUnit) {
        this(id, clock, distributionStatisticConfig, pauseDetector, baseTimeUnit, false);
    }

    /**
     * Creates a new timer.
     * @param id The timer's name and tags.
     * @param clock The clock used to measure latency.
     * @param distributionStatisticConfig Configuration determining which distribution
     * statistics are sent.
     * @param pauseDetector Compensation for coordinated omission.
     * @param baseTimeUnit The time scale of this timer.
     * @param supportsAggregablePercentiles Indicates whether the registry supports
     * percentile approximations from histograms.
     */
    protected AbstractTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector, TimeUnit baseTimeUnit, boolean supportsAggregablePercentiles) {
        this(id, clock, pauseDetector, baseTimeUnit,
                defaultHistogram(clock, distributionStatisticConfig, supportsAggregablePercentiles));
    }

    /**
     * Creates a new timer.
     * @param id The timer's name and tags.
     * @param clock The clock used to measure latency.
     * @param pauseDetector Compensation for coordinated omission.
     * @param baseTimeUnit The time scale of this timer.
     * @param histogram Histogram.
     * @since 1.11.0
     */
    protected AbstractTimer(Id id, Clock clock, PauseDetector pauseDetector, TimeUnit baseTimeUnit,
            Histogram histogram) {
        super(id);
        this.clock = clock;
        this.baseTimeUnit = baseTimeUnit;
        initPauseDetector(pauseDetector);
        this.histogram = histogram;
    }

    /**
     * Creates a default histogram.
     * @param clock The clock used to measure latency.
     * @param distributionStatisticConfig Configuration determining which distribution
     * statistics are sent.
     * @param supportsAggregablePercentiles Indicates whether the registry supports
     * percentile approximations from histograms.
     * @return a default histogram
     * @since 1.11.0
     */
    protected static Histogram defaultHistogram(Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            boolean supportsAggregablePercentiles) {
        if (distributionStatisticConfig.isPublishingPercentiles()) {
            // hdr-based histogram
            return new TimeWindowPercentileHistogram(clock, distributionStatisticConfig, supportsAggregablePercentiles);
        }
        if (distributionStatisticConfig.isPublishingHistogram()) {
            // fixed boundary histograms, which have a slightly better memory footprint
            // when we don't need Micrometer-computed percentiles
            return new TimeWindowFixedBoundaryHistogram(clock, distributionStatisticConfig,
                    supportsAggregablePercentiles);
        }
        return NoopHistogram.INSTANCE;
    }

    private void initPauseDetector(PauseDetector pauseDetectorType) {
        if (pauseDetectorType instanceof NoPauseDetector) {
            return;
        }
        pauseDetector = (org.LatencyUtils.PauseDetector) pauseDetectorCache.computeIfAbsent(pauseDetectorType,
                detector -> {
                    if (detector instanceof ClockDriftPauseDetector) {
                        ClockDriftPauseDetector clockDriftPauseDetector = (ClockDriftPauseDetector) detector;
                        return new SimplePauseDetector(clockDriftPauseDetector.getSleepInterval().toNanos(),
                                clockDriftPauseDetector.getPauseThreshold().toNanos(), 1, false);
                    }
                    return null;
                });

        if (pauseDetector instanceof SimplePauseDetector) {
            this.intervalEstimator = new TimeCappedMovingAverageIntervalEstimator(128, 10000000000L, pauseDetector);

            pauseDetector.addListener((pauseLength, pauseEndTime) -> {
                if (intervalEstimator != null) {
                    long estimatedInterval = ((IntervalEstimator) intervalEstimator).getEstimatedInterval(pauseEndTime);
                    long observedLatencyMinbar = pauseLength - estimatedInterval;
                    if (observedLatencyMinbar >= estimatedInterval) {
                        recordValueWithExpectedInterval(observedLatencyMinbar, estimatedInterval);
                    }
                }
            });
        }
    }

    private void recordValueWithExpectedInterval(long nanoValue, long expectedIntervalBetweenValueSamples) {
        record(nanoValue, TimeUnit.NANOSECONDS);
        if (expectedIntervalBetweenValueSamples <= 0)
            return;
        for (long missingValue = nanoValue
                - expectedIntervalBetweenValueSamples; missingValue >= expectedIntervalBetweenValueSamples; missingValue -= expectedIntervalBetweenValueSamples) {
            record(missingValue, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public <T> T recordCallable(Callable<T> f) throws Exception {
        final long s = clock.monotonicTime();
        try {
            return f.call();
        }
        finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public <T> T record(Supplier<T> f) {
        final long s = clock.monotonicTime();
        try {
            return f.get();
        }
        finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public boolean record(BooleanSupplier f) {
        final long s = clock.monotonicTime();
        try {
            return f.getAsBoolean();
        }
        finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public int record(IntSupplier f) {
        final long s = clock.monotonicTime();
        try {
            return f.getAsInt();
        }
        finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public long record(LongSupplier f) {
        final long s = clock.monotonicTime();
        try {
            return f.getAsLong();
        }
        finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public double record(DoubleSupplier f) {
        final long s = clock.monotonicTime();
        try {
            return f.getAsDouble();
        }
        finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public void record(Runnable f) {
        final long s = clock.monotonicTime();
        try {
            f.run();
        }
        finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public final void record(long amount, TimeUnit unit) {
        if (amount >= 0) {
            histogram.recordLong(TimeUnit.NANOSECONDS.convert(amount, unit));
            recordNonNegative(amount, unit);

            if (intervalEstimator != null) {
                ((IntervalEstimator) intervalEstimator).recordInterval(clock.monotonicTime());
            }
        }
        else {
            log.log(() -> "'amount' should not be negative but was: " + amount);
        }
    }

    protected abstract void recordNonNegative(long amount, TimeUnit unit);

    @Override
    public HistogramSnapshot takeSnapshot() {
        return histogram.takeSnapshot(count(), totalTime(TimeUnit.NANOSECONDS), max(TimeUnit.NANOSECONDS));
    }

    @Override
    public TimeUnit baseTimeUnit() {
        return baseTimeUnit;
    }

    @Override
    public void close() {
        histogram.close();
        if (pauseDetector != null) {
            pauseDetector.shutdown();
        }
    }

}
