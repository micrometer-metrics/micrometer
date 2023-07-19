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

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.prometheus.metrics.core.datapoints.DistributionDataPoint;
import io.prometheus.metrics.model.registry.Collector;
import io.prometheus.metrics.model.snapshots.DistributionDataPointSnapshot;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.prometheus.metrics.model.snapshots.Unit.nanosToSeconds;

/**
 * Long task timer that can either be backed by a Prometheus histogram or by a Prometheus
 * summary.
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
public class PrometheusLongTaskTimer<T extends Collector, S extends DistributionDataPointSnapshot>
        extends PrometheusMeter<T, S> implements LongTaskTimer {

    private final DistributionDataPoint dataPoint;

    private final Max max;

    private AtomicInteger activeTasksCount = new AtomicInteger(0);

    public PrometheusLongTaskTimer(Id id, Max max, T histogramOrSummary, DistributionDataPoint dataPoint) {
        super(id, histogramOrSummary);
        this.max = max;
        this.dataPoint = dataPoint;
    }

    class Sample extends LongTaskTimer.Sample {

        private final long startTimeNanos;

        private volatile double durationSeconds = 0;

        Sample() {
            this.startTimeNanos = System.nanoTime();
        }

        @Override
        public long stop() {
            long durationNanos = System.nanoTime() - startTimeNanos;
            durationSeconds = nanosToSeconds(durationNanos);
            dataPoint.observe(durationSeconds);
            max.observe(durationSeconds);
            activeTasksCount.decrementAndGet();
            return durationNanos;
        }

        @Override
        public double duration(TimeUnit unit) {
            return toUnit(durationSeconds, unit);
        }

    }

    @Override
    public Sample start() {
        activeTasksCount.incrementAndGet();
        return new Sample();
    }

    @Override
    public double duration(TimeUnit unit) {
        return toUnit(collect().getSum(), unit);
    }

    @Override
    public int activeTasks() {
        return activeTasksCount.get();
    }

    @Override
    public double max(TimeUnit unit) {
        return toUnit(max.get(), unit);
    }

    @Override
    public TimeUnit baseTimeUnit() {
        return TimeUnit.SECONDS;
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        return HistogramSnapshot.empty(collect().getCount(), duration(baseTimeUnit()), max(baseTimeUnit()));
    }

}
