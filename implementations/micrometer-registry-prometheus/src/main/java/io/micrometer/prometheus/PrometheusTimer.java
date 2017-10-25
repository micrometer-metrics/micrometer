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
package io.micrometer.prometheus;

import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.histogram.StatsConfig;
import io.micrometer.core.instrument.step.StepDouble;
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public class PrometheusTimer extends AbstractTimer implements Timer {
    private final LongAdder count = new LongAdder();
    private final LongAdder totalTime = new LongAdder();
    private final StepDouble max;

    PrometheusTimer(Id id, Clock clock, StatsConfig statsConfig, long maxStepMillis) {
        super(id, clock, statsConfig);
        this.max = new StepDouble(clock, maxStepMillis);
    }

    @Override
    protected void recordNonNegative(long amount, TimeUnit unit) {
        count.increment();
        long nanoAmount = TimeUnit.NANOSECONDS.convert(amount, unit);
        totalTime.add(nanoAmount);
        max.getCurrent().add(Math.max(nanoAmount - max.getCurrent().doubleValue(), 0));
    }

    @Override
    public long count() {
        return count.longValue();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return TimeUtils.nanosToUnit(totalTime.doubleValue(), unit);
    }

    @Override
    public double max(TimeUnit unit) {
        return TimeUtils.nanosToUnit(max.poll(), unit);
    }
}
