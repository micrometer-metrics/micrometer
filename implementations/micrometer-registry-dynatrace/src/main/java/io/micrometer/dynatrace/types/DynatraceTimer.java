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

import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Resettable {@link Timer} implementation for Dynatrace exporters.
 *
 * @author Georg Pirklbauer
 * @since 1.9.0
 */
public final class DynatraceTimer extends AbstractTimer implements DynatraceSummarySnapshotSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynatraceTimer.class);

    // Configuration that will set the Histogram in AbstractTimer to a NoopHistogram.
    private static final DistributionStatisticConfig NOOP_HISTOGRAM_CONFIG = DistributionStatisticConfig.builder()
        .percentilesHistogram(false)
        .percentiles()
        .serviceLevelObjectives()
        .build();

    private final DynatraceSummary summary = new DynatraceSummary();

    public DynatraceTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector, TimeUnit baseTimeUnit) {
        // make sure the Histogram in AbstractTimer is always a NoopHistogram by disabling
        // the respective config options
        super(id, clock, distributionStatisticConfig.merge(NOOP_HISTOGRAM_CONFIG), pauseDetector, baseTimeUnit, false);

        if (distributionStatisticConfig.isPublishingPercentiles()
                || distributionStatisticConfig.isPublishingHistogram()) {
            LOGGER.warn(
                    "Histogram config on DistributionStatisticConfig is currently ignored. Collecting summary statistics.");
        }
    }

    /**
     * @deprecated see {@link DynatraceSummarySnapshotSupport#hasValues()}.
     */
    @Override
    @Deprecated
    public boolean hasValues() {
        return summary.getCount() > 0;
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshot() {
        return takeSummarySnapshot(baseTimeUnit());
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshot(TimeUnit unit) {
        return convertIfNecessary(summary.takeSummarySnapshot(), unit);
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshotAndReset() {
        return takeSummarySnapshotAndReset(baseTimeUnit());
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshotAndReset(TimeUnit unit) {
        return convertIfNecessary(summary.takeSummarySnapshotAndReset(), unit);
    }

    DynatraceSummarySnapshot convertIfNecessary(DynatraceSummarySnapshot snapshot, TimeUnit unit) {
        if (unit == baseTimeUnit()) {
            return snapshot;
        }

        return new DynatraceSummarySnapshot(unit.convert((long) snapshot.getMin(), baseTimeUnit()),
                unit.convert((long) snapshot.getMax(), baseTimeUnit()),
                unit.convert((long) snapshot.getTotal(), baseTimeUnit()), snapshot.getCount());
    }

    @Override
    protected void recordNonNegative(long amount, TimeUnit unit) {
        // store everything in baseTimeUnit
        long inBaseUnit = baseTimeUnit().convert(amount, unit);
        summary.recordNonNegative(inBaseUnit);
    }

    /**
     * Using this method is not synchronized and might give inconsistent results when
     * multiple getters are called sequentially. It is recommended to
     * {@link #takeSummarySnapshot(TimeUnit) take a snapshot} and use the getters on the
     * {@link DynatraceSummarySnapshot} instead.
     */
    @Override
    public long count() {
        return summary.getCount();
    }

    /**
     * Using this method is not synchronized and might give inconsistent results when
     * multiple getters are called sequentially. It is recommended to
     * {@link #takeSummarySnapshot(TimeUnit) take a snapshot} and use the getters on the
     * {@link DynatraceSummarySnapshot} instead.
     */
    @Override
    public double totalTime(TimeUnit unit) {
        return unit.convert((long) summary.getTotal(), baseTimeUnit());
    }

    /**
     * Using this method is not synchronized and might give inconsistent results when
     * multiple getters are called sequentially. It is recommended to
     * {@link #takeSummarySnapshot(TimeUnit) take a snapshot} and use the getters on the
     * {@link DynatraceSummarySnapshot} instead.
     */
    @Override
    public double max(TimeUnit unit) {
        return unit.convert((long) summary.getMax(), baseTimeUnit());
    }

    /**
     * @deprecated since 1.9.10. Using this method is not synchronized and might give
     * inconsistent results when multiple getters are called sequentially. It is
     * recommended to {@link #takeSummarySnapshot(TimeUnit) take a snapshot} and use the
     * getters on the {@link DynatraceSummarySnapshot} instead.
     */
    @Deprecated
    public double min(TimeUnit unit) {
        return unit.convert((long) summary.getMin(), baseTimeUnit());
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        DynatraceSummarySnapshot dtSnapshot = takeSummarySnapshot();
        return HistogramSnapshot.empty(dtSnapshot.getCount(), dtSnapshot.getTotal(), dtSnapshot.getMax());
    }

}
