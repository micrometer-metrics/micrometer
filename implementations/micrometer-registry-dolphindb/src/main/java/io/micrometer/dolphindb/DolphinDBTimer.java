package io.micrometer.dolphindb;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.util.TimeUtils;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
public class DolphinDBTimer extends AbstractTimer {
    private static final CountAtBucket[] EMPTY_HISTOGRAM = new CountAtBucket[0];

    private final LongAdder count = new LongAdder();

    private final LongAdder totalTime = new LongAdder();

    private final TimeWindowMax max;

    @Nullable
    private final Histogram histogram;

    DolphinDBTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        super(id, clock, DistributionStatisticConfig.builder()
                .percentilesHistogram(false)
                .serviceLevelObjectives()
                .build()
                .merge(distributionStatisticConfig),
                pauseDetector, TimeUnit.SECONDS, false);

        this.max = new TimeWindowMax(clock, distributionStatisticConfig);

        if (distributionStatisticConfig.isPublishingHistogram()) {
            histogram = new TimeWindowFixedBoundaryHistogram(clock,
                    DistributionStatisticConfig.builder()
                            .expiry(Duration.ofDays(1835))
                            .bufferLength(1)
                            .build()
                            .merge(distributionStatisticConfig), true);
        }
        else {
            histogram = null;
        }
    }

    @Override
    protected void recordNonNegative(long amount, TimeUnit unit) {
        count.increment();
        long nanoAmount = TimeUnit.NANOSECONDS.convert(amount, unit);
        totalTime.add(nanoAmount);
        max.record(nanoAmount, TimeUnit.NANOSECONDS);

        if (histogram != null) {
            histogram.recordLong(nanoAmount);
        }
    }

    @Override
    public long count() {
        return count.longValue();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return TimeUtils.nanosToUnit(totalTime.doubleValue(), unit);
    }

    @Override
    public double max(TimeUnit unit) {
        return max.poll(unit);
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
