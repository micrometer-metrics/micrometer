/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.prometheusnative;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.prometheus.metrics.core.datapoints.DistributionDataPoint;
import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.model.snapshots.HistogramSnapshot.HistogramDataPointSnapshot;

public class PrometheusHistogram extends PrometheusMeter<Histogram, HistogramDataPointSnapshot>
        implements DistributionSummary {

    private final Max max;

    private final DistributionDataPoint dataPoint;

    public PrometheusHistogram(Id id, Max max, Histogram histogram, DistributionDataPoint dataPoint) {
        super(id, histogram);
        this.max = max;
        this.dataPoint = dataPoint;
    }

    @Override
    public void record(double amount) {
        dataPoint.observe(amount);
        max.observe(amount);
    }

    @Override
    public long count() {
        return collect().getCount();
    }

    @Override
    public double totalAmount() {
        return collect().getSum();
    }

    @Override
    public double max() {
        return max.get();
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        return HistogramSnapshot.empty(count(), totalAmount(), max());
    }

}
