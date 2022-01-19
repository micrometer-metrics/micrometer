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
package io.micrometer.api.instrument;

import io.micrometer.api.instrument.distribution.*;
import io.micrometer.api.instrument.util.MeterEquivalence;
import io.micrometer.api.lang.Nullable;

public abstract class AbstractDistributionSummary extends AbstractMeter implements DistributionSummary {
    protected final Histogram histogram;
    private final double scale;

    protected AbstractDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig, double scale,
                                          boolean supportsAggregablePercentiles) {
        super(id);
        this.scale = scale;

        if (distributionStatisticConfig.isPublishingPercentiles()) {
            // hdr-based histogram
            this.histogram = new TimeWindowPercentileHistogram(clock, distributionStatisticConfig, supportsAggregablePercentiles);
        } else if (distributionStatisticConfig.isPublishingHistogram()) {
            // fixed boundary histograms, which have a slightly better memory footprint
            // when we don't need Micrometer-computed percentiles
            this.histogram = new TimeWindowFixedBoundaryHistogram(clock, distributionStatisticConfig, supportsAggregablePercentiles);
        } else {
            // noop histogram
            this.histogram = NoopHistogram.INSTANCE;
        }
    }

    @Override
    public final void record(double amount) {
        if (amount >= 0) {
            double scaledAmount = this.scale * amount;
            histogram.recordDouble(scaledAmount);
            recordNonNegative(scaledAmount);
        }
    }

    protected abstract void recordNonNegative(double amount);

    @Override
    public HistogramSnapshot takeSnapshot() {
        return histogram.takeSnapshot(count(), totalAmount(), max());
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(@Nullable Object o) {
        return MeterEquivalence.equals(this, o);
    }

    @Override
    public int hashCode() {
        return MeterEquivalence.hashCode(this);
    }
}
