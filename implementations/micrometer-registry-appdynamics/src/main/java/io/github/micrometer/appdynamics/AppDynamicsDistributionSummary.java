package io.github.micrometer.appdynamics;

import io.github.micrometer.appdynamics.aggregation.MetricAggregator;
import io.github.micrometer.appdynamics.aggregation.MetricSnapshot;
import io.github.micrometer.appdynamics.aggregation.MetricSnapshotProvider;
import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

import java.util.concurrent.TimeUnit;

/**
 * @author Ricardo Veloso
 */
public class AppDynamicsDistributionSummary extends AbstractDistributionSummary implements MetricSnapshotProvider {

    private final MetricAggregator aggregator = new MetricAggregator();

    protected AppDynamicsDistributionSummary(Id id, Clock clock, double scale) {
        super(id, clock, DistributionStatisticConfig.NONE, scale, false);
    }

    @Override
    protected void recordNonNegative(double amount) {
        aggregator.recordNonNegative((long) amount);
    }

    @Override
    public long count() {
        return aggregator.count();
    }

    @Override
    public double totalAmount() {
        return aggregator.total();
    }

    @Override
    public double max() {
        return aggregator.max();
    }

    public double min() {
        return aggregator.min();
    }

    @Override
    public MetricSnapshot snapshot() {
        MetricSnapshot snapshot = new MetricSnapshot(count(), min(), max(), totalAmount());
        aggregator.reset();
        return snapshot;
    }

    @Override
    public MetricSnapshot snapshot(TimeUnit unit) {
        return snapshot();
    }

}
