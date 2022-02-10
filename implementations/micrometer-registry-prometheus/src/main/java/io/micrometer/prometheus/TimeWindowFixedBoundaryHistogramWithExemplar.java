/*
 * Copyright 2020 VMware, Inc.
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

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.TimeWindowFixedBoundaryHistogram;
import io.micrometer.core.lang.NonNull;
import io.micrometer.core.lang.Nullable;
import io.prometheus.client.exemplars.Exemplar;
import io.prometheus.client.exemplars.HistogramExemplarSampler;

/**
 * Custom TimeWindowFixedBoundaryHistogram Notice that we can't have access to
 * existing bucket (private in parent class) and we are not able to inherit from
 * AbstractTimeWindowHistogram as this Abstract Base class is also private in
 * micrometer-core package
 */
public class TimeWindowFixedBoundaryHistogramWithExemplar extends TimeWindowFixedBoundaryHistogram {
        
        // Store bucket first time we record data in associated Distribution
        private double[] buckets = null;
        // Allow to track Exemplar associated with each Bucket
        private Map<Double, AtomicReference<Exemplar>> examplarsByBuckets;
        // The ExemplarSampler, provide for end-user Spring/SpringBoot configuration
        // that allow to get a new Exemplar.
        // This examplar will be automatically provider if the Java OpenTelemetry Agent
        // is provide to JVM. If it is not provide,
        // prometheus scraper behavior will be the same as usual
        @Nullable
        private final HistogramExemplarSampler exemplarSampler;

        // Getter exemplar
        public Exemplar getExemplar(double bucket) {
                if (examplarsByBuckets.get(bucket) != null) {
                        return examplarsByBuckets.get(bucket).get();
                }
                else return null;
                
        }

        public TimeWindowFixedBoundaryHistogramWithExemplar(Clock clock, DistributionStatisticConfig config,
                        boolean supportsAggregablePercentiles) {
                this(clock, config, supportsAggregablePercentiles, null);
        }

        public TimeWindowFixedBoundaryHistogramWithExemplar(Clock clock, DistributionStatisticConfig config,
                        boolean supportsAggregablePercentiles, HistogramExemplarSampler exemplarSampler) {
                super(clock, config, supportsAggregablePercentiles);
                this.exemplarSampler = exemplarSampler;
                this.examplarsByBuckets = new ConcurrentHashMap<Double, AtomicReference<Exemplar>>();
        }

        /**
         * The method call on record
         */
        @Override
        public void recordDouble(double value) {

                if (this.buckets == null) {
                        loadBuckets();
                }

                if (exemplarSampler != null) {
                        updateExemplar(value, exemplarSampler);
                }

                super.recordDouble(value);
        }

        /**
         * Initial loading of buckets becaus there is no possibility to grab
         * configuration (most of parents methods are private and base Abstract class is
         * also private)
         */
        private synchronized void loadBuckets() {
                HistogramSnapshot histo = this.takeSnapshot(0, 0, 0);
                this.buckets = new double[histo.histogramCounts().length];
                for (int i = 0; i < histo.histogramCounts().length; i++) {
                        examplarsByBuckets.put(histo.histogramCounts()[i].bucket(), new AtomicReference<Exemplar>());
                        buckets[i] = histo.histogramCounts()[i].bucket();
                }
                examplarsByBuckets.put(Double.POSITIVE_INFINITY, new AtomicReference<Exemplar>());
                Arrays.sort(buckets); // Pour être certain que les buckets sont bien ordonnés
        }

        /**
         * We search for bucket and update Examplar in corresponding map place
         */
        private void updateExemplar(double amount, @NonNull HistogramExemplarSampler exemplarSampler) {
                Exemplar prev;
                Exemplar next;
                double inf = Double.NEGATIVE_INFINITY, sup = Double.POSITIVE_INFINITY;

                // Finding of down and top bucket
                for (int i = 0; i < buckets.length; i++) {
                        if (amount < buckets[i]) {
                                sup = buckets[i];
                                break;
                        } else {
                                inf = buckets[i];
                        }
                }

                // Update of Bucket's examplar
                do {
                        prev = examplarsByBuckets.get(sup).get();
                        next = exemplarSampler.sample(amount, inf, sup, prev);
                        if (next == null || next == prev) {
                                return;
                        }
                } while (!examplarsByBuckets.get(sup).compareAndSet(prev, next));
        }
}
