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

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * @author Jon Schneider
 */
public class StepTimer extends AbstractTimer {
    private final StepLong count;
    private final StepLong total;
    private final StepLong max;

    /**
     * Create a new instance.
     */
    public StepTimer(Id id, Clock clock, HistogramConfig histogramConfig, long step) {
        super(id, clock, histogramConfig);
        this.count = new StepLong(clock, step);
        this.total = new StepLong(clock, step);
        this.max = new StepLong(clock, step);
    }

    @Override
    protected void recordNonNegative(long amount, TimeUnit unit) {
        long nanoAmount = (long) TimeUtils.convert(amount, unit, TimeUnit.NANOSECONDS);
        count.getCurrent().add(1);
        total.getCurrent().add(nanoAmount);
        max.getCurrent().add(Math.max(nanoAmount - max.getCurrent().longValue(), 0));
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
        return TimeUtils.nanosToUnit(max.poll(), unit);
    }

    @Override
    public Iterable<Measurement> measure() {
        return Arrays.asList(
            new Measurement(() -> (double) count(), Statistic.Count),
            new Measurement(() -> totalTime(TimeUnit.NANOSECONDS), Statistic.TotalTime),
            new Measurement(() -> totalTime(TimeUnit.NANOSECONDS), Statistic.Max)
        );
    }
}
