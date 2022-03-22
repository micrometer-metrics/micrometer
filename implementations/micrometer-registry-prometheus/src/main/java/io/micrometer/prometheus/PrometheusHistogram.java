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

package io.micrometer.prometheus;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReferenceArray;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.Histogram;
import io.micrometer.core.instrument.distribution.TimeWindowFixedBoundaryHistogram;
import io.micrometer.core.lang.Nullable;
import io.prometheus.client.exemplars.Exemplar;
import io.prometheus.client.exemplars.HistogramExemplarSampler;

/**
 * Internal {@link Histogram} implementation for Prometheus that handles {@link Exemplar exemplars}.
 *
 * @author Jonatan Ivanov
 */
class PrometheusHistogram extends TimeWindowFixedBoundaryHistogram {
    private final double[] buckets;
    private final AtomicReferenceArray<Exemplar> exemplars;
    @Nullable private final HistogramExemplarSampler exemplarSampler;

    PrometheusHistogram(Clock clock, DistributionStatisticConfig config, @Nullable HistogramExemplarSampler exemplarSampler) {
        super(
                clock,
                DistributionStatisticConfig.builder()
                        .expiry(Duration.ofDays(1825)) // effectively never roll over
                        .bufferLength(1)
                        .build()
                        .merge(config),
                true
        );

        this.exemplarSampler = exemplarSampler;
        if (isExemplarsEnabled()) {
            double[] originalBuckets = super.getBuckets();
            if (originalBuckets[originalBuckets.length - 1] != Double.POSITIVE_INFINITY) {
                this.buckets = Arrays.copyOf(originalBuckets, originalBuckets.length + 1);
                this.buckets[buckets.length - 1] = Double.POSITIVE_INFINITY;
            }
            else {
                this.buckets = originalBuckets;
            }
            this.exemplars = new AtomicReferenceArray<>(this.buckets.length);
        }
        else {
            this.buckets = null;
            this.exemplars = null;
        }
    }

    boolean isExemplarsEnabled() {
        return exemplarSampler != null;
    }

    @Override
    public void recordDouble(double value) {
        super.recordDouble(value);
        if (isExemplarsEnabled()) {
            updateExemplar(value);
        }
    }

    @Override
    public void recordLong(long value) {
        super.recordLong(value);
        if (isExemplarsEnabled()) {
            updateExemplar(value);
        }
    }

    private void updateExemplar(double value) {
        int index = this.leastLessThanOrEqualTo(value);
        index = (index == -1) ? exemplars.length() - 1 : index;
        updateExemplar(value, index);
    }

    private void updateExemplar(double value, int index) {
        double bucketFrom = (index == 0) ? Double.NEGATIVE_INFINITY : buckets[index - 1];
        double bucketTo = buckets[index];
        Exemplar prev;
        Exemplar next;

        do {
            prev = exemplars.get(index);
            next = exemplarSampler.sample(value, bucketFrom, bucketTo, prev);
            if (next == null || next == prev) {
                return;
            }
        }
        while (!exemplars.compareAndSet(index, prev, next));
    }

    @Nullable Exemplar[] exemplars() {
        if (isExemplarsEnabled()) {
            Exemplar[] exemplarsArray = new Exemplar[this.exemplars.length()];
            for (int i = 0; i < this.exemplars.length(); i++) {
                exemplarsArray[i] = this.exemplars.get(i);
            }

            return exemplarsArray;
        }
        else {
            return null;
        }
    }

    /**
     * The least bucket that is less than or equal to a sample.
     */
    private int leastLessThanOrEqualTo(double key) {
        double[] buckets = getBuckets();
        int low = 0;
        int high = buckets.length - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (buckets[mid] < key)
                low = mid + 1;
            else if (buckets[mid] > key)
                high = mid - 1;
            else
                return mid; // exact match
        }

        return low < buckets.length ? low : -1;
    }
}
