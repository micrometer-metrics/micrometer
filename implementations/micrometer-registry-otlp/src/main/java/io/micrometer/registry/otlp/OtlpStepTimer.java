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
package io.micrometer.registry.otlp;

import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

class OtlpStepTimer extends AbstractTimer {

    private final LongAdder count = new LongAdder();

    private final LongAdder total = new LongAdder();

    private final OtlpStepTuple2<Long, Long> countTotal;

    private final StepMax max;

    /**
     * Create a new {@code StepTimer}.
     * @param id ID
     * @param clock clock
     * @param distributionStatisticConfig distribution statistic configuration
     * @param pauseDetector pause detector
     * @param baseTimeUnit base time unit
     * @param stepDurationMillis step in milliseconds
     */
    public OtlpStepTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector, TimeUnit baseTimeUnit, long stepDurationMillis) {
        super(id, clock, pauseDetector, baseTimeUnit, OtlpMeterRegistry.getHistogram(clock, distributionStatisticConfig,
                AggregationTemporality.DELTA, stepDurationMillis));
        countTotal = new OtlpStepTuple2<>(clock, stepDurationMillis, 0L, 0L, count::sumThenReset, total::sumThenReset);
        max = new StepMax(clock, stepDurationMillis);
    }

    @Override
    protected void recordNonNegative(final long amount, final TimeUnit unit) {
        final long nanoAmount = (long) TimeUtils.convert(amount, unit, TimeUnit.NANOSECONDS);
        count.add(1);
        total.add(nanoAmount);
        max.record(nanoAmount);
    }

    @Override
    public long count() {
        return countTotal.poll1();
    }

    @Override
    public double totalTime(final TimeUnit unit) {
        return TimeUtils.nanosToUnit(countTotal.poll2(), unit);
    }

    @Override
    public double max(final TimeUnit unit) {
        return TimeUtils.nanosToUnit(max.poll(), unit);
    }

    /**
     * This is an internal method not meant for general use.
     * <p>
     * Force a rollover of the values returned by a step meter and never roll over again
     * after. See: {@code StepMeter} and {@code StepTimer}
     */
    void _closingRollover() {
        countTotal._closingRollover();
        max._closingRollover();
        if (histogram instanceof OtlpStepBucketHistogram) { // can be noop
            ((OtlpStepBucketHistogram) histogram)._closingRollover();
        }
    }

}
