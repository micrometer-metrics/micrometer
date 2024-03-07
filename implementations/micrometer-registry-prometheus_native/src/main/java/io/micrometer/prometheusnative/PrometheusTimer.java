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

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.prometheus.metrics.core.datapoints.DistributionDataPoint;
import io.prometheus.metrics.model.registry.Collector;
import io.prometheus.metrics.model.snapshots.DistributionDataPointSnapshot;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static io.prometheus.metrics.model.snapshots.Unit.nanosToSeconds;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Backed by either a Histogram or a Summary.
 *
 * @param <T> {@link io.prometheus.metrics.core.metrics.Histogram Histogram} or
 * {@link io.prometheus.metrics.core.metrics.Summary Summary}.
 * @param <S>
 * {@link io.prometheus.metrics.model.snapshots.HistogramSnapshot.HistogramDataPointSnapshot
 * HistogramDataPointSnapshot} if {@code T} is
 * {@link io.prometheus.metrics.core.metrics.Histogram Histogram},
 * {@link io.prometheus.metrics.model.snapshots.SummarySnapshot.SummaryDataPointSnapshot
 * SummaryDataPointSnapshot} if {@code T} is
 * {@link io.prometheus.metrics.core.metrics.Summary Summary}.
 */
public class PrometheusTimer<T extends Collector, S extends DistributionDataPointSnapshot> extends PrometheusMeter<T, S>
        implements Timer {

    private final DistributionDataPoint dataPoint;

    private final Max max;

    public PrometheusTimer(Id id, Max max, T histogramOrSummary, DistributionDataPoint dataPoint) {
        super(id, histogramOrSummary);
        this.max = max;
        this.dataPoint = dataPoint;
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        double amountSeconds = nanosToSeconds(unit.toNanos(amount));
        dataPoint.observe(amountSeconds);
        max.observe(amountSeconds);
    }

    @Override
    public <O> O record(Supplier<O> f) {
        io.prometheus.metrics.core.datapoints.Timer timer = dataPoint.startTimer();
        try {
            return f.get();
        }
        finally {
            max.observe(timer.observeDuration());
        }
    }

    @Override
    public <O> O recordCallable(Callable<O> f) throws Exception {
        io.prometheus.metrics.core.datapoints.Timer timer = dataPoint.startTimer();
        try {
            return f.call();
        }
        finally {
            max.observe(timer.observeDuration());
        }
    }

    @Override
    public void record(Runnable f) {
        io.prometheus.metrics.core.datapoints.Timer timer = dataPoint.startTimer();
        try {
            f.run();
        }
        finally {
            max.observe(timer.observeDuration());
        }
    }

    @Override
    public long count() {
        return collect().getCount();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return toUnit(collect().getSum(), unit);
    }

    @Override
    public double max(TimeUnit unit) {
        return toUnit(max.get(), unit);
    }

    @Override
    public TimeUnit baseTimeUnit() {
        return SECONDS;
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        return HistogramSnapshot.empty(count(), totalTime(baseTimeUnit()), max(baseTimeUnit()));
    }

}
