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
package io.micrometer.appdynamics;

import io.micrometer.appdynamics.aggregation.MetricAggregator;
import io.micrometer.appdynamics.aggregation.MetricSnapshot;
import io.micrometer.appdynamics.aggregation.MetricSnapshotProvider;
import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

import java.util.concurrent.TimeUnit;

/**
 * AppDynamics {@link DistributionSummary} that keeps track of the min value.
 *
 * @author Ricardo Veloso
 */
public class AppDynamicsDistributionSummary extends AbstractDistributionSummary implements MetricSnapshotProvider {

    private final MetricAggregator aggregator = new MetricAggregator();

    protected AppDynamicsDistributionSummary(Id id, Clock clock, double scale) {
        super(id, clock, DistributionStatisticConfig.NONE, scale, false);
    }

    @Override
    protected void recordNonNegative(double amount) {
        aggregator.recordNonNegative((long) amount);
    }

    @Override
    public long count() {
        return aggregator.count();
    }

    @Override
    public double totalAmount() {
        return aggregator.total();
    }

    @Override
    public double max() {
        return aggregator.max();
    }

    public double min() {
        return aggregator.min();
    }

    @Override
    public MetricSnapshot snapshot() {
        MetricSnapshot snapshot = new MetricSnapshot(count(), min(), max(), totalAmount());
        aggregator.reset();
        return snapshot;
    }

    @Override
    public MetricSnapshot snapshot(TimeUnit unit) {
        return snapshot();
    }

}
