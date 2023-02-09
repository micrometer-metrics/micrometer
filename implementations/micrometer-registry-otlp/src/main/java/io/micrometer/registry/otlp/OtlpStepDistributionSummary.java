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
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.core.instrument.step.StepDistributionSummary;

class OtlpStepDistributionSummary extends StepDistributionSummary {

    @Nullable
    private final Histogram countBucketHistogram;

    /**
     * Create a new {@code StepDistributionSummary}.
     * @param id ID
     * @param clock clock
     * @param distributionStatisticConfig distribution static configuration
     * @param scale scale
     * @param stepMillis step in milliseconds
     */
    public OtlpStepDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            double scale, long stepMillis) {
        super(id, clock, DistributionStatisticConfig.builder().percentilesHistogram(false).serviceLevelObjectives()
                .build().merge(distributionStatisticConfig), scale, stepMillis, false);
        if (distributionStatisticConfig.isPublishingHistogram()) {
            this.countBucketHistogram = new TimeWindowFixedBoundaryHistogram(clock, distributionStatisticConfig, true,
                    false);
        }
        else {
            this.countBucketHistogram = null;
        }
    }

    @Override
    protected void recordNonNegative(double amount) {
        super.recordNonNegative(amount);
        if (this.countBucketHistogram != null) {
            this.countBucketHistogram.recordDouble(amount);
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
