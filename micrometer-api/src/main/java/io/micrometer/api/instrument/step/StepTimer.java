/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.api.instrument.step;

import io.micrometer.api.instrument.AbstractTimer;
import io.micrometer.api.instrument.Clock;
import io.micrometer.api.instrument.util.TimeUtils;
import io.micrometer.api.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.api.instrument.distribution.TimeWindowMax;
import io.micrometer.api.instrument.distribution.pause.PauseDetector;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author Jon Schneider
 */
public class StepTimer extends AbstractTimer {
    private final LongAdder count = new LongAdder();
    private final LongAdder total = new LongAdder();
    private final StepTuple2<Long, Long> countTotal;
    private final TimeWindowMax max;

    /**
     * Create a new {@code StepTimer}.
     *
     * @param id                            ID
     * @param clock                         clock
     * @param distributionStatisticConfig   distribution statistic configuration
     * @param pauseDetector                 pause detector
     * @param baseTimeUnit                  base time unit
     * @param stepDurationMillis                    step in milliseconds
     * @param supportsAggregablePercentiles whether it supports aggregable percentiles
     */
    public StepTimer(final Id id, final Clock clock, final DistributionStatisticConfig distributionStatisticConfig,
        final PauseDetector pauseDetector, final TimeUnit baseTimeUnit, final long stepDurationMillis,
        final boolean supportsAggregablePercentiles
    ) {
        super(id, clock, distributionStatisticConfig, pauseDetector, baseTimeUnit, supportsAggregablePercentiles);
        countTotal = new StepTuple2<>(clock, stepDurationMillis, 0L, 0L, count::sumThenReset, total::sumThenReset);
        max = new TimeWindowMax(clock, distributionStatisticConfig);
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
}
