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
import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;

import java.util.concurrent.TimeUnit;

/**
 * AppDynamics {@link Timer} that keeps track of the min value.
 *
 * @author Ricardo Veloso
 */
public class AppDynamicsTimer extends AbstractTimer implements MetricSnapshotProvider {

    private final MetricAggregator aggregator = new MetricAggregator();

    public AppDynamicsTimer(Id id, Clock clock, PauseDetector pauseDetector, TimeUnit baseTimeUnit, long stepMillis) {
        super(id, clock, DistributionStatisticConfig.NONE, pauseDetector, baseTimeUnit, false);
    }

    @Override
    protected void recordNonNegative(long amount, TimeUnit unit) {
        long value = TimeUnit.NANOSECONDS.convert(amount, unit);
        aggregator.recordNonNegative(value);
    }

    @Override
    public long count() {
        return aggregator.count();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return unit.convert(aggregator.total(), TimeUnit.NANOSECONDS);
    }

    public double min(TimeUnit unit) {
        return unit.convert(aggregator.min(), TimeUnit.NANOSECONDS);
    }

    @Override
    public double max(TimeUnit unit) {
        return unit.convert(aggregator.max(), TimeUnit.NANOSECONDS);
    }

    @Override
    public MetricSnapshot snapshot() {
        return snapshot(baseTimeUnit());
    }

    @Override
    public MetricSnapshot snapshot(TimeUnit unit) {
        MetricSnapshot snapshot = new MetricSnapshot(count(), min(unit), max(unit), totalTime(unit));
        aggregator.reset();
        return snapshot;
    }

}
