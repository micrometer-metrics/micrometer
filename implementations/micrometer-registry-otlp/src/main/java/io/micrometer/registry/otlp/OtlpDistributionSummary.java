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
package io.micrometer.registry.otlp;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.StepHistogram;
import io.micrometer.core.instrument.step.StepMax;
import io.micrometer.core.instrument.step.StepTuple2;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * A {@link io.micrometer.core.instrument.DistributionSummary} implementation customised
 * for OTLP protocol to export data based on the
 * {@link io.opentelemetry.proto.metrics.v1.AggregationTemporality} configured. In case of
 * Delta aggregation, this uses a {@link StepMax} and {@link StepHistogram} to measure the
 * distribution.
 *
 * @author Tommy Ludwig
 * @author Lenin Jaganathan
 */
class OtlpDistributionSummary extends AbstractDistributionSummary implements StartTimeAwareMeter {

    private final long startTimeNanos;

    private final LongAdder count = new LongAdder();

    private final DoubleAdder total = new DoubleAdder();

    @Nullable
    private final StepTuple2<Long, Double> countTotal;

    @Nullable
    private final StepMax max;

    OtlpDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig, double scale,
            long stepMillis, io.opentelemetry.proto.metrics.v1.AggregationTemporality aggregationTemporality) {
        super(id, scale,
                OtlpMeterRegistry.getHistogram(clock, stepMillis, distributionStatisticConfig, aggregationTemporality));

        this.startTimeNanos = TimeUnit.MILLISECONDS.toNanos(clock.wallTime());
        countTotal = AggregationTemporality.isDelta(aggregationTemporality)
                ? new StepTuple2<>(clock, stepMillis, 0L, 0.0, count::sumThenReset, total::sumThenReset) : null;
        max = AggregationTemporality.isDelta(aggregationTemporality) ? new StepMax(clock, stepMillis) : null;
    }

    @Override
    protected void recordNonNegative(double amount) {
        count.add(1);
        total.add(amount);
        if (max != null) {
            max.record(amount);
        }
    }

    @Override
    public long count() {
        return countTotal == null ? count.longValue() : countTotal.poll1();
    }

    @Override
    public double totalAmount() {
        return countTotal == null ? total.doubleValue() : countTotal.poll2();
    }

    @Override
    public double max() {
        return max == null ? 0 : max.poll();
    }

    @Override
    public long getStartTimeNanos() {
        return startTimeNanos;
    }

}
