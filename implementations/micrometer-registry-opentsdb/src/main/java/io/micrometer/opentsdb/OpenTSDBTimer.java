/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.opentsdb;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.util.TimeUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * {@link io.micrometer.core.instrument.Timer} for OpenTSDB.
 *
 * @author Jon Schneider
 * @author Nikolay Ustinov
 * @since 1.4.0
 */
public class OpenTSDBTimer extends AbstractTimer {

    private static final CountAtBucket[] EMPTY_HISTOGRAM = new CountAtBucket[0];

    private final LongAdder count = new LongAdder();

    private final LongAdder totalTime = new LongAdder();

    private final TimeWindowMax max;

    @Nullable
    private final Histogram histogram;

    OpenTSDBTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector, @Nullable OpenTSDBFlavor flavor) {
        super(id, clock,
                DistributionStatisticConfig.builder()
                    .percentilesHistogram(false)
                    .serviceLevelObjectives()
                    .build()
                    .merge(distributionStatisticConfig),
                pauseDetector, TimeUnit.SECONDS, false);

        this.max = new TimeWindowMax(clock, distributionStatisticConfig);

        if (distributionStatisticConfig.isPublishingHistogram()) {
            if (flavor == null) {
                histogram = new TimeWindowFixedBoundaryHistogram(clock,
                        DistributionStatisticConfig.builder()
                            .expiry(Duration.ofDays(1825)) // effectively
                                                           // never
                                                           // roll
                                                           // over
                            .bufferLength(1)
                            .build()
                            .merge(distributionStatisticConfig),
                        true);
            }
            else if (OpenTSDBFlavor.VictoriaMetrics.equals(flavor)) {
                histogram = new FixedBoundaryVictoriaMetricsHistogram();
            }
            else {
                histogram = null;
            }
        }
        else {
            histogram = null;
        }
    }

    @Override
    protected void recordNonNegative(long amount, TimeUnit unit) {
        count.increment();
        long nanoAmount = TimeUnit.NANOSECONDS.convert(amount, unit);
        totalTime.add(nanoAmount);
        max.record(nanoAmount, TimeUnit.NANOSECONDS);

        if (histogram != null) {
            histogram.recordLong(nanoAmount);
        }
    }

    @Override
    public long count() {
        return count.longValue();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return TimeUtils.nanosToUnit(totalTime.doubleValue(), unit);
    }

    @Override
    public double max(TimeUnit unit) {
        return max.poll(unit);
    }

    /**
     * For Prometheus we cannot use the histogram counts from HistogramSnapshot, as it is
     * based on a rolling histogram. Prometheus requires a histogram that accumulates
     * values over the lifetime of the app.
     * @return Cumulative histogram buckets.
     */
    public CountAtBucket[] histogramCounts() {
        return histogram == null ? EMPTY_HISTOGRAM : histogram.takeSnapshot(0, 0, 0).histogramCounts();
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        HistogramSnapshot snapshot = super.takeSnapshot();

        if (histogram == null) {
            return snapshot;
        }

        return new HistogramSnapshot(snapshot.count(), snapshot.total(), snapshot.max(), snapshot.percentileValues(),
                histogramCounts(), snapshot::outputSummary);
    }

}
