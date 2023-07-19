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
import io.micrometer.core.instrument.distribution.*;

public abstract class AbstractDistributionSummary extends AbstractMeter implements DistributionSummary {

    protected final Histogram histogram;

    private final double scale;

    protected AbstractDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            double scale, boolean supportsAggregablePercentiles) {
        this(id, scale, defaultHistogram(clock, distributionStatisticConfig, supportsAggregablePercentiles));
    }

    /**
     * Creates an {@code AbstractDistributionSummary} instance.
     * @param id meter ID
     * @param scale scale
     * @param histogram histogram
     * @since 1.11.0
     */
    protected AbstractDistributionSummary(Id id, double scale, @Nullable Histogram histogram) {
        super(id);
        this.scale = scale;
        this.histogram = histogram == null ? NoopHistogram.INSTANCE : histogram;
    }

    /**
     * Creates a default histogram.
     * @param clock clock
     * @param distributionStatisticConfig distribution statistic configuration
     * @param supportsAggregablePercentiles whether to support aggregable percentiles
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

}
