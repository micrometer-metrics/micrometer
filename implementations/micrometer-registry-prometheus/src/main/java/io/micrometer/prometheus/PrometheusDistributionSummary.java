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

import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.core.lang.Nullable;
import io.prometheus.client.exemplars.Exemplar;
import io.prometheus.client.exemplars.HistogramExemplarSampler;

import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * {@link DistributionSummary} for Prometheus.
 *
 * @author Jon Schneider
 * @author Jonatan Ivanov
 */
public class PrometheusDistributionSummary extends AbstractDistributionSummary {

    private static final CountAtBucket[] EMPTY_HISTOGRAM = new CountAtBucket[0];

    private final LongAdder count = new LongAdder();

    private final DoubleAdder amount = new DoubleAdder();

    private final TimeWindowMax max;

    private final HistogramFlavor histogramFlavor;

    @Nullable
    private final Histogram histogram;

    private boolean exemplarsEnabled = false;

    PrometheusDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            double scale, HistogramFlavor histogramFlavor, @Nullable HistogramExemplarSampler exemplarSampler) {
        super(id, clock, DistributionStatisticConfig.builder().percentilesHistogram(false).serviceLevelObjectives()
                .build().merge(distributionStatisticConfig), scale, false);

        this.histogramFlavor = histogramFlavor;
        this.max = new TimeWindowMax(clock, distributionStatisticConfig);

        if (distributionStatisticConfig.isPublishingHistogram()) {
            switch (histogramFlavor) {
                case Prometheus:
                    PrometheusHistogram prometheusHistogram = new PrometheusHistogram(clock,
                            distributionStatisticConfig, exemplarSampler);
                    this.histogram = prometheusHistogram;
                    this.exemplarsEnabled = prometheusHistogram.isExemplarsEnabled();
                    break;
                case VictoriaMetrics:
                    this.histogram = new FixedBoundaryVictoriaMetricsHistogram();
                    break;
                default:
                    this.histogram = null;
                    break;
            }
        }
        else {
            this.histogram = null;
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

    @Nullable
    Exemplar[] exemplars() {
        if (exemplarsEnabled) {
            return ((PrometheusHistogram) histogram).exemplars();
        }
        else {
            return null;
        }
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

    /**
     * For Prometheus we cannot use the histogram counts from HistogramSnapshot, as it is
     * based on a rolling histogram. Prometheus requires a histogram that accumulates
     * values over the lifetime of the app.
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

        return new HistogramSnapshot(snapshot.count(), snapshot.total(), snapshot.max(), snapshot.percentileValues(),
                histogramCounts(), snapshot::outputSummary);
    }

}
