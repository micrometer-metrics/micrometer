/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.registry.otlp;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.cumulative.CumulativeTimer;
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.util.TimeUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

class OtlpTimer extends CumulativeTimer implements StartTimeAwareMeter {

    private final long startTimeNanos;

    @Nullable
    private final Histogram monotonicCountBucketHistogram;

    OtlpTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector,
            TimeUnit baseTimeUnit) {
        super(id, clock, DistributionStatisticConfig.builder().percentilesHistogram(false) // avoid
                                                                                           // a
                                                                                           // histogram
                                                                                           // for
                                                                                           // percentiles/SLOs
                                                                                           // in
                                                                                           // the
                                                                                           // super
                .serviceLevelObjectives() // we will use a different implementation here
                                          // instead
                .build().merge(distributionStatisticConfig), pauseDetector, baseTimeUnit, false);
        this.startTimeNanos = TimeUnit.MILLISECONDS.toNanos(clock.wallTime());
        // CumulativeTimer doesn't produce monotonic histogram counts; maybe it should
        // Also, we need to customize the histogram behavior to not return cumulative
        // counts across buckets
        if (distributionStatisticConfig.isPublishingHistogram()) {
            this.monotonicCountBucketHistogram = new TimeWindowFixedBoundaryHistogram(clock,
                    DistributionStatisticConfig.builder().expiry(Duration.ofDays(1825)) // effectively
                                                                                        // never
                                                                                        // roll
                                                                                        // over
                            .bufferLength(1).build().merge(distributionStatisticConfig),
                    true, false);
        }
        else {
            this.monotonicCountBucketHistogram = null;
        }
    }

    @Override
    protected void recordNonNegative(long amount, TimeUnit unit) {
        super.recordNonNegative(amount, unit);
        if (this.monotonicCountBucketHistogram != null) {
            this.monotonicCountBucketHistogram.recordLong((long) TimeUtils.convert(amount, unit, TimeUnit.NANOSECONDS));
        }
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        HistogramSnapshot snapshot = super.takeSnapshot();
        if (monotonicCountBucketHistogram == null) {
            return snapshot;
        }

        CountAtBucket[] histogramCounts = this.monotonicCountBucketHistogram.takeSnapshot(0, 0, 0).histogramCounts();
        return new HistogramSnapshot(snapshot.count(), snapshot.total(), snapshot.max(), snapshot.percentileValues(),
                histogramCounts, snapshot::outputSummary);
    }

    @Override
    public long getStartTimeNanos() {
        return this.startTimeNanos;
    }

}
