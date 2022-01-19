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
package io.micrometer.prometheus;

import io.micrometer.api.instrument.AbstractDistributionSummary;
import io.micrometer.api.instrument.Clock;
import io.micrometer.api.instrument.distribution.*;
import io.micrometer.api.instrument.util.MeterEquivalence;
import io.micrometer.api.lang.Nullable;

import java.time.Duration;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

public class PrometheusDistributionSummary extends AbstractDistributionSummary {
    private static final CountAtBucket[] EMPTY_HISTOGRAM = new CountAtBucket[0];
    @Nullable
    private final Histogram histogram;
    private final LongAdder count = new LongAdder();
    private final DoubleAdder amount = new DoubleAdder();
    private final TimeWindowMax max;

    private final HistogramFlavor histogramFlavor;

    PrometheusDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig, double scale, HistogramFlavor histogramFlavor) {
        super(id, clock,
                DistributionStatisticConfig.builder()
                        .percentilesHistogram(false)
                        .serviceLevelObjectives()
                        .build()
                        .merge(distributionStatisticConfig),
                scale, false);

        this.histogramFlavor = histogramFlavor;
        this.max = new TimeWindowMax(clock, distributionStatisticConfig);

        if (distributionStatisticConfig.isPublishingHistogram()) {
            switch (histogramFlavor) {
                case Prometheus:
                    histogram = new TimeWindowFixedBoundaryHistogram(clock, DistributionStatisticConfig.builder()
                            .expiry(Duration.ofDays(1825)) // effectively never roll over
                            .bufferLength(1)
                            .build()
                            .merge(distributionStatisticConfig), true);
                    break;
                case VictoriaMetrics:
                    histogram = new FixedBoundaryVictoriaMetricsHistogram();
                    break;
                default:
                    histogram = null;
                    break;
            }
        } else {
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

    public HistogramFlavor histogramFlavor() {
        return histogramFlavor;
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

    /**
     * For Prometheus we cannot use the histogram counts from HistogramSnapshot, as it is based on a
     * rolling histogram. Prometheus requires a histogram that accumulates values over the lifetime of the app.
     *
     * @return Cumulative histogram buckets.
     */
    public CountAtBucket[] histogramCounts() {
        return histogram == null ? EMPTY_HISTOGRAM : histogram.takeSnapshot(0, 0, 0).histogramCounts();
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
}
