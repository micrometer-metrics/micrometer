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
import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepTuple2;
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * A {@link io.micrometer.core.instrument.Timer} implementation customised for OTLP
 * protocol to export data based on the
 * {@link io.opentelemetry.proto.metrics.v1.AggregationTemporality} configured. In case of
 * Delta aggregation, this uses a {@link StepMax} and {@link StepHistogram} to measure the
 * distribution.
 *
 * @author Tommy Ludwig
 * @author Lenin Jaganathan
 */
class OtlpTimer extends AbstractTimer implements StartTimeAwareMeter {

    private final long startTimeNanos;

    private final LongAdder count = new LongAdder();

    private final LongAdder total = new LongAdder();

    @Nullable
    private final StepTuple2<Long, Long> countTotal;

    @Nullable
    private final StepMax max;

    OtlpTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig, long stepMillis,
            PauseDetector pauseDetector, TimeUnit baseTimeUnit,
            io.opentelemetry.proto.metrics.v1.AggregationTemporality aggregationTemporality) {
        super(id, clock, pauseDetector, baseTimeUnit,
                OtlpMeterRegistry.getHistogram(clock, stepMillis, distributionStatisticConfig, aggregationTemporality));
        this.startTimeNanos = TimeUnit.MILLISECONDS.toNanos(clock.wallTime());
        countTotal = AggregationTemporality.isDelta(aggregationTemporality)
                ? new StepTuple2<>(clock, stepMillis, 0L, 0L, count::sumThenReset, total::sumThenReset) : null;
        max = AggregationTemporality.isDelta(aggregationTemporality) ? new StepMax(clock, stepMillis) : null;
    }

    @Override
    protected void recordNonNegative(long amount, TimeUnit unit) {
        final long nanoAmount = (long) TimeUtils.convert(amount, unit, TimeUnit.NANOSECONDS);
        count.add(1);
        total.add(nanoAmount);
        if (max != null) {
            max.record(nanoAmount);
        }
    }

    @Override
    public long count() {
        return countTotal == null ? count.longValue() : countTotal.poll1();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return TimeUtils.nanosToUnit(countTotal == null ? total.longValue() : countTotal.poll2(), unit);
    }

    @Override
    public double max(TimeUnit unit) {
        return TimeUtils.nanosToUnit(max == null ? 0 : max.poll(), unit);
    }

    @Override
    public long getStartTimeNanos() {
        return startTimeNanos;
    }

}
