package io.micrometer.dolphindb;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.*;

import java.time.Duration;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
public class DolphinDBDistributionSummary extends AbstractDistributionSummary{

    private static final CountAtBucket[] EMPTY_HISTOGRAM = new CountAtBucket[0];

    private final LongAdder count = new LongAdder();

    private final DoubleAdder amount = new DoubleAdder();

    private final TimeWindowMax max;

    @Nullable
    private final Histogram histogram;

    DolphinDBDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
                                double scale) {
        super(id, clock,
                DistributionStatisticConfig.builder()
                        .percentilesHistogram(false)
                        .serviceLevelObjectives()
                        .build()
                        .merge(distributionStatisticConfig),
                scale, false);

        this.max = new TimeWindowMax(clock, distributionStatisticConfig);

        if (distributionStatisticConfig.isPublishingHistogram()) {
            histogram = new TimeWindowFixedBoundaryHistogram(clock,
                    DistributionStatisticConfig.builder()
                            .expiry(Duration.ofDays(1825))
                            .bufferLength(1)
                            .build()
                            .merge(distributionStatisticConfig),
                    true);
        }
        else {
            histogram = null;
        }
    }

    @Override
    protected void recordNonNegative(double amount) {
        count.increment();
        this.amount.add(amount);
        max.record(amount);

        if (histogram != null)
            histogram.recordDouble(amount);
    }

    @Override
    public long count() {
        return count.longValue();
    }

    @Override
    public double totalAmount() {
        return amount.doubleValue();
    }

    @Override
    public double max() {
        return max.poll();
    }
    public CountAtBucket[] histogramCounts() {
        return histogram == null ? EMPTY_HISTOGRAM : histogram.takeSnapshot(0, 0, 0).histogramCounts();
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        HistogramSnapshot snapshot = super.takeSnapshot();

        if (histogram == null) {
            return snapshot;
        }

        return new HistogramSnapshot(snapshot.count(), snapshot.total(), snapshot.max(), snapshot.percentileValues(),
                histogramCounts(), snapshot::outputSummary);
    }

}

