package io.micrometer.appdynamics;

import io.micrometer.appdynamics.aggregation.MetricAggregator;
import io.micrometer.appdynamics.aggregation.MetricSnapshot;
import io.micrometer.appdynamics.aggregation.MetricSnapshotProvider;
import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.concurrent.TimeUnit;

/**
 * AppDynamics timer that keeps track of the min value
 *
 * @author Ricardo Veloso
 */
public class AppDynamicsTimer extends AbstractTimer implements MetricSnapshotProvider {

    private final MetricAggregator aggregator = new MetricAggregator();

    public AppDynamicsTimer(Id id, Clock clock, PauseDetector pauseDetector, TimeUnit baseTimeUnit, long stepMillis) {
        super(id, clock, DistributionStatisticConfig.NONE, pauseDetector, baseTimeUnit, false);
    }

    @Override
    protected void recordNonNegative(long amount, TimeUnit unit) {
        long value = (long) TimeUtils.convert(amount, unit, TimeUnit.NANOSECONDS);
        aggregator.recordNonNegative(value);
    }

    @Override
    public long count() {
        return aggregator.count();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return TimeUtils.nanosToUnit(aggregator.total(), unit);
    }

    public double min(TimeUnit unit) {
        return TimeUtils.nanosToUnit(aggregator.min(), unit);
    }

    @Override
    public double max(TimeUnit unit) {
        return TimeUtils.nanosToUnit(aggregator.max(), unit);
    }

    @Override
    public MetricSnapshot snapshot() {
        return snapshot(baseTimeUnit());
    }

    @Override
    public MetricSnapshot snapshot(TimeUnit unit) {
        MetricSnapshot snapshot = new MetricSnapshot(count(), min(unit), max(unit), totalTime(unit));
        aggregator.reset();
        return snapshot;
    }

}
