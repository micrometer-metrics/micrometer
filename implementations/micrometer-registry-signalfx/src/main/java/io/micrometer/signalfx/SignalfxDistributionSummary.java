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
package io.micrometer.signalfx;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.StepBucketHistogram;
import io.micrometer.core.instrument.step.StepDistributionSummary;

/**
 * A StepDistributionSummary which provides support for multiple flavours of Histograms to
 * be recorded based on {@link SignalFxConfig#publishCumulativeHistogram()} and
 * {@link SignalFxConfig#publishDeltaHistogram()}.
 *
 * @author Bogdan Drutu
 * @author Mateusz Rzeszutek
 * @author Lenin Jaganathan
 */
final class SignalfxDistributionSummary extends StepDistributionSummary {

    @Nullable
    private final StepBucketHistogram stepBucketHistogram;

    SignalfxDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            double scale, long stepMillis, boolean isDelta) {
        super(id, clock, distributionStatisticConfig, scale, stepMillis, defaultHistogram(clock,
                CumulativeHistogramConfigUtil.updateConfig(distributionStatisticConfig, isDelta), false));

        if (distributionStatisticConfig.isPublishingHistogram() && isDelta) {
            stepBucketHistogram = new StepBucketHistogram(clock, stepMillis,
                    DistributionStatisticConfig.builder()
                        .serviceLevelObjectives(CumulativeHistogramConfigUtil
                            .addPositiveInfBucket(distributionStatisticConfig.getServiceLevelObjectiveBoundaries()))
                        .build()
                        .merge(distributionStatisticConfig),
                    false, true);
        }
        else {
            stepBucketHistogram = null;
        }
    }

    @Override
    protected void recordNonNegative(double amount) {
        if (stepBucketHistogram != null) {
            stepBucketHistogram.recordDouble(amount);
        }
        super.recordNonNegative(amount);
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        HistogramSnapshot currentSnapshot = super.takeSnapshot();
        if (stepBucketHistogram == null) {
            return currentSnapshot;
        }
        return new HistogramSnapshot(currentSnapshot.count(), currentSnapshot.total(), currentSnapshot.max(),
                currentSnapshot.percentileValues(), stepBucketHistogram.poll(), currentSnapshot::outputSummary);
    }

}
