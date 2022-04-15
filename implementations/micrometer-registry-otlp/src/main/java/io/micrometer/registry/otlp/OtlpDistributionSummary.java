package io.micrometer.registry.otlp;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.cumulative.CumulativeDistributionSummary;
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.lang.Nullable;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

class OtlpDistributionSummary extends CumulativeDistributionSummary implements StartTimeAwareMeter {

    private static final CountAtBucket[] EMPTY_HISTOGRAM = new CountAtBucket[0];

    private final long startTimeNanos;

    @Nullable
    private final Histogram histogram;

    OtlpDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig, double scale, boolean supportsAggregablePercentiles) {
        super(id, clock, DistributionStatisticConfig.builder()
                .percentilesHistogram(false) // avoid a histogram for percentiles/SLOs in the super
                .serviceLevelObjectives() // we will use a different implementation here instead
                .build()
                .merge(distributionStatisticConfig), scale, false);
        this.startTimeNanos = TimeUnit.MILLISECONDS.toNanos(clock.wallTime());
        // CumulativeDistributionSummary doesn't produce monotonic histogram counts; maybe it should
        // Also, we need to customize the histogram behavior to not return cumulative counts across buckets
        if (distributionStatisticConfig.isPublishingHistogram()) {
            this.histogram = new TimeWindowFixedBoundaryHistogram(clock, DistributionStatisticConfig.builder()
                    .expiry(Duration.ofDays(1825)) // effectively never roll over
                    .bufferLength(1)
                    .build()
                    .merge(distributionStatisticConfig), true, false);
        } else {
            this.histogram = null;
        }
    }

    @Override
    protected void recordNonNegative(double amount) {
        super.recordNonNegative(amount);
        if (this.histogram != null) {
            this.histogram.recordDouble(amount);
        }
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        HistogramSnapshot snapshot = super.takeSnapshot();
        if (histogram == null) {
            return snapshot;
        }

        return new HistogramSnapshot(snapshot.count(),
                snapshot.total(),
                snapshot.max(),
                snapshot.percentileValues(),
                histogramCounts(),
                snapshot::outputSummary);
    }

    private CountAtBucket[] histogramCounts() {
        return this.histogram == null ? EMPTY_HISTOGRAM : this.histogram.takeSnapshot(0, 0, 0).histogramCounts();
    }

    @Override
    public long getStartTimeNanos() {
        return this.startTimeNanos;
    }
}
