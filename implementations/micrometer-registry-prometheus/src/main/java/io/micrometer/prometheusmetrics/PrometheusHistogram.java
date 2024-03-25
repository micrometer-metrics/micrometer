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

package io.micrometer.prometheusmetrics;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.Histogram;
import io.micrometer.core.instrument.distribution.TimeWindowFixedBoundaryHistogram;
import io.prometheus.metrics.core.exemplars.ExemplarSampler;
import io.prometheus.metrics.model.snapshots.Exemplar;
import io.prometheus.metrics.model.snapshots.Exemplars;

import java.time.Duration;
import java.util.Arrays;

/**
 * Internal {@link Histogram} implementation for Prometheus that handles {@link Exemplar
 * exemplars}.
 *
 * @author Jonatan Ivanov
 */
class PrometheusHistogram extends TimeWindowFixedBoundaryHistogram {

    @Nullable
    private final ExemplarSampler exemplarSampler;

    PrometheusHistogram(Clock clock, DistributionStatisticConfig config,
            @Nullable ExemplarSamplerFactory exemplarSamplerFactory) {
        super(clock, DistributionStatisticConfig.builder()
            // effectively never rolls over
            .expiry(Duration.ofDays(1825))
            .bufferLength(1)
            .build()
            .merge(config), true);

        if (exemplarSamplerFactory != null) {
            double[] buckets = getBuckets();
            if (buckets[buckets.length - 1] != Double.POSITIVE_INFINITY) {
                buckets = Arrays.copyOf(buckets, buckets.length + 1);
                buckets[buckets.length - 1] = Double.POSITIVE_INFINITY;
            }
            this.exemplarSampler = exemplarSamplerFactory.createExemplarSampler(buckets);
        }
        else {
            this.exemplarSampler = null;
        }
    }

    @Override
    public void recordDouble(double value) {
        super.recordDouble(value);
        if (exemplarSampler != null) {
            exemplarSampler.observe(value);
        }
    }

    @Override
    public void recordLong(long value) {
        super.recordLong(value);
        if (exemplarSampler != null) {
            exemplarSampler.observe(value);
        }
    }

    Exemplars exemplars() {
        return exemplarSampler != null ? this.exemplarSampler.collect() : Exemplars.EMPTY;
    }

}
