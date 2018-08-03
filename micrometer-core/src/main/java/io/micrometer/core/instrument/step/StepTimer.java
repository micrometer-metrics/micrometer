/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
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

    @Deprecated
    public StepTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
                     PauseDetector pauseDetector, TimeUnit baseTimeUnit, long stepMillis) {
        this(id, clock, distributionStatisticConfig, pauseDetector, baseTimeUnit,  stepMillis,false);
    }

    @SuppressWarnings("ConstantConditions")
    public StepTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
                     PauseDetector pauseDetector, TimeUnit baseTimeUnit, long stepMillis, boolean supportsAggregablePercentiles) {
        super(id, clock, distributionStatisticConfig, pauseDetector, baseTimeUnit, supportsAggregablePercentiles);
        this.count = new StepLong(clock, stepMillis);
        this.total = new StepLong(clock, stepMillis);
        this.max = new TimeWindowMax(clock, distributionStatisticConfig);
    }

    @Override
    protected void recordNonNegative(long amount, TimeUnit unit) {
        long nanoAmount = (long) TimeUtils.convert(amount, unit, TimeUnit.NANOSECONDS);
        count.getCurrent().add(1);
        total.getCurrent().add(nanoAmount);
        max.record(amount, unit);
    }

    @Override
    public long count() {
        return (long) count.poll();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return TimeUtils.nanosToUnit(total.poll(), unit);
    }

    @Override
    public double max(TimeUnit unit) {
        return max.poll(unit);
    }
}
