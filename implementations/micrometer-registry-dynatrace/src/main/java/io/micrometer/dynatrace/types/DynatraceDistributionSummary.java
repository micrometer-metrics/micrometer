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
package io.micrometer.dynatrace.types;

import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Resettable {@link DistributionSummary} implementation for Dynatrace exporters.
 *
 * @author Georg Pirklbauer
 * @since 1.9.0
 */
public final class DynatraceDistributionSummary extends AbstractDistributionSummary
        implements DynatraceSummarySnapshotSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynatraceDistributionSummary.class);

    // Configuration that will set the Histogram in AbstractDistributionSummary to a
    // NoopHistogram.
    private static final DistributionStatisticConfig NOOP_HISTOGRAM_CONFIG = DistributionStatisticConfig.builder()
        .percentilesHistogram(false)
        .percentiles()
        .serviceLevelObjectives()
        .build();

    private final DynatraceSummary summary = new DynatraceSummary();

    public DynatraceDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            double scale) {
        // make sure the Histogram in AbstractDistributionSummary is always a
        // NoopHistogram by disabling the respective config options
        super(id, clock, distributionStatisticConfig.merge(NOOP_HISTOGRAM_CONFIG), scale, false);

        if (distributionStatisticConfig.isPublishingPercentiles()
                || distributionStatisticConfig.isPublishingHistogram()) {
            LOGGER.warn(
                    "Histogram config on DistributionStatisticConfig is currently ignored. Collecting summary statistics.");
        }
    }

    @Override
    protected void recordNonNegative(double amount) {
        summary.recordNonNegative(amount);
    }

    /**
     * Using this method is not synchronized and might give inconsistent results when
     * multiple getters are called sequentially. It is recommended to
     * {@link #takeSummarySnapshot() take a snapshot} and use the getters on the
     * {@link DynatraceSummarySnapshot} instead.
     */
    @Override
    public long count() {
        return summary.getCount();
    }

    /**
     * Using this method is not synchronized and might give inconsistent results when
     * multiple getters are called sequentially. It is recommended to
     * {@link #takeSummarySnapshot() take a snapshot} and use the getters on the
     * {@link DynatraceSummarySnapshot} instead.
     */
    @Override
    public double totalAmount() {
        return summary.getTotal();
    }

    /**
     * Using this method is not synchronized and might give inconsistent results when
     * multiple getters are called sequentially. It is recommended to
     * {@link #takeSummarySnapshot() take a snapshot} and use the getters on the
     * {@link DynatraceSummarySnapshot} instead.
     */
    @Override
    public double max() {
        return summary.getMax();
    }

    /**
     * @deprecated since 1.9.10. Using this method is not synchronized and might give
     * inconsistent results when multiple getters are called sequentially. It is
     * recommended to {@link #takeSummarySnapshot() take a snapshot} and use the getters
     * on the {@link DynatraceSummarySnapshot} instead.
     */
    @Deprecated
    public double min() {
        return summary.getMin();
    }

    /**
     * @deprecated see {@link DynatraceSummarySnapshotSupport#hasValues()}.
     */
    @Override
    @Deprecated
    public boolean hasValues() {
        return count() > 0;
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshot() {
        return summary.takeSummarySnapshot();
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshot(TimeUnit timeUnit) {
        LOGGER.debug("Called takeSummarySnapshot with a TimeUnit on a DistributionSummary. Ignoring TimeUnit.");
        return takeSummarySnapshot();
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshotAndReset() {
        return summary.takeSummarySnapshotAndReset();
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshotAndReset(TimeUnit unit) {
        LOGGER.debug("Called takeSummarySnapshotAndReset with a TimeUnit on a DistributionSummary. Ignoring TimeUnit.");
        return takeSummarySnapshotAndReset();
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        LOGGER.warn("Called takeSnapshot on a Dynatrace Distribution Summary, no percentiles will be exported.");
        DynatraceSummarySnapshot dtSnapshot = takeSummarySnapshot();
        return HistogramSnapshot.empty(dtSnapshot.getCount(), dtSnapshot.getTotal(), dtSnapshot.getMax());
    }

}
