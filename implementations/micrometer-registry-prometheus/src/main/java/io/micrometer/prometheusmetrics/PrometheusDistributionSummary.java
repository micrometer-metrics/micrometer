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
package io.micrometer.prometheusmetrics;

import io.micrometer.common.lang.NonNull;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.distribution.*;
import io.prometheus.metrics.core.exemplars.ExemplarSampler;
import io.prometheus.metrics.model.snapshots.Exemplar;

import java.util.concurrent.atomic.AtomicReference;
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

    @Nullable
    private final Histogram histogram;

    @Nullable
    private final ExemplarSampler exemplarSampler;

    @Nullable
    private final AtomicReference<Exemplar> lastExemplar;

    private boolean histogramExemplarsEnabled = false;

    PrometheusDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            double scale, @Nullable ExemplarSampler exemplarSampler) {
        super(id, clock,
                DistributionStatisticConfig.builder()
                    .percentilesHistogram(false)
                    .serviceLevelObjectives()
                    .build()
                    .merge(distributionStatisticConfig),
                scale, false);

        this.max = new TimeWindowMax(clock, distributionStatisticConfig);

        if (distributionStatisticConfig.isPublishingHistogram()) {
            PrometheusHistogram prometheusHistogram = new PrometheusHistogram(clock, distributionStatisticConfig,
                    exemplarSampler);
            this.histogram = prometheusHistogram;
            this.histogramExemplarsEnabled = prometheusHistogram.isExemplarsEnabled();
        }
        else {
            this.histogram = null;
        }

        if (!this.histogramExemplarsEnabled && exemplarSampler != null) {
            this.exemplarSampler = exemplarSampler;
            this.lastExemplar = new AtomicReference<>();
        }
        else {
            this.exemplarSampler = null;
            this.lastExemplar = null;
        }
    }

    @Override
    protected void recordNonNegative(double amount) {
        count.increment();
        this.amount.add(amount);
        max.record(amount);

        if (histogram != null) {
            histogram.recordDouble(amount);
        }
        if (!histogramExemplarsEnabled && exemplarSampler != null) {
            updateLastExemplar(amount, exemplarSampler);
        }
    }

    // Similar to exemplar.updateAndGet(...) but it does nothing if the next value is null
    private void updateLastExemplar(double amount, @NonNull ExemplarSampler exemplarSampler) {
        // Exemplar prev;
        // Exemplar next;
        // do {
        // prev = lastExemplar.get();
        // next = exemplarSampler.sample(amount, prev);
        // }
        // while (next != null && next != prev && !lastExemplar.compareAndSet(prev,
        // next));
    }

    @Nullable
    Exemplar[] histogramExemplars() {
        if (histogramExemplarsEnabled) {
            return ((PrometheusHistogram) histogram).exemplars();
        }
        else {
            return null;
        }
    }

    @Nullable
    Exemplar lastExemplar() {
        if (histogramExemplarsEnabled) {
            return ((PrometheusHistogram) histogram).lastExemplar();
        }
        else {
            return lastExemplar != null ? lastExemplar.get() : null;
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
