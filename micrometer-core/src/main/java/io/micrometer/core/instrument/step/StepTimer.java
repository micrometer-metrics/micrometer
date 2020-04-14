/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.step;

import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.TimeWindowMax;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.concurrent.TimeUnit;

/**
 * @author Jon Schneider
 */
public class StepTimer extends AbstractTimer {
    private final StepLong count;
    private final StepLong total;
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

        count = new StepLong(clock, stepDurationMillis);
        total = new StepLong(clock, stepDurationMillis);
        max = new TimeWindowMax(clock, distributionStatisticConfig);
    }

    @Override
    protected void recordNonNegative(final long amount, final TimeUnit unit) {
        final long nanoAmount = (long) TimeUtils.convert(amount, unit, TimeUnit.NANOSECONDS);
        count.getCurrent().add(1);
        total.getCurrent().add(nanoAmount);
        max.record(nanoAmount);
    }

    @Override
    public long count() {
        return count.poll();
    }

    @Override
    public double totalTime(final TimeUnit unit) {
        return TimeUtils.nanosToUnit(total.poll(), unit);
    }

    @Override
    public double max(final TimeUnit unit) {
        return TimeUtils.nanosToUnit(max.poll(), unit);
    }
}
