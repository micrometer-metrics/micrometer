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
package io.micrometer.registry.otlp;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepTimer;
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.concurrent.TimeUnit;

class OtlpStepTimer extends StepTimer {

    @Nullable
    private final Histogram countBucketHistogram;

    private static final CountAtBucket[] EMPTY_HISTOGRAM = new CountAtBucket[0];

    /**
     * Create a new {@code StepTimer}.
     * @param id ID
     * @param clock clock
     * @param distributionStatisticConfig distribution statistic configuration
     * @param pauseDetector pause detector
     * @param baseTimeUnit base time unit
     * @param stepDurationMillis step in milliseconds
     */
    public OtlpStepTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector, TimeUnit baseTimeUnit, long stepDurationMillis) {
        super(id, clock, DistributionStatisticConfig.builder().percentilesHistogram(false).serviceLevelObjectives()
                .build().merge(distributionStatisticConfig), pauseDetector, baseTimeUnit, stepDurationMillis, false);
        if (distributionStatisticConfig.isPublishingHistogram()) {
            this.countBucketHistogram = new TimeWindowFixedBoundaryHistogram(clock, distributionStatisticConfig, true,
                    false);
        }
        else {
            this.countBucketHistogram = null;
        }
    }

    @Override
    protected void recordNonNegative(long amount, TimeUnit unit) {
        super.recordNonNegative(amount, unit);
        if (this.countBucketHistogram != null) {
            this.countBucketHistogram.recordLong((long) TimeUtils.convert((double) amount, unit, TimeUnit.NANOSECONDS));
        }
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        HistogramSnapshot snapshot = super.takeSnapshot();
        if (countBucketHistogram == null) {
            return snapshot;
        }

        CountAtBucket[] histogramCounts = this.countBucketHistogram.takeSnapshot(0, 0, 0).histogramCounts();
        return new HistogramSnapshot(snapshot.count(), snapshot.total(), snapshot.max(), snapshot.percentileValues(),
                histogramCounts, snapshot::outputSummary);
    }

}
