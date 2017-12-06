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
package io.micrometer.core.instrument.cumulative;

import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Jon Schneider
 */
public class CumulativeTimer extends AbstractTimer {
    private final AtomicLong count;
    private final AtomicLong total;
    private final AtomicLong max;

    /**
     * Create a new instance.
     */
    public CumulativeTimer(Id id, Clock clock, HistogramConfig histogramConfig) {
        super(id, clock, histogramConfig);
        this.count = new AtomicLong();
        this.total = new AtomicLong();
        this.max = new AtomicLong();
    }

    @Override
    protected void recordNonNegative(long amount, TimeUnit unit) {
        long nanoAmount = (long) TimeUtils.convert(amount, unit, TimeUnit.NANOSECONDS);
        count.getAndAdd(1);
        total.getAndAdd(nanoAmount);
        updateMax(nanoAmount);
    }

    @Override
    public long count() {
        return (long) count.get();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return TimeUtils.nanosToUnit(total.get(), unit);
    }

    @Override
    public double max(TimeUnit unit) {
        return TimeUtils.nanosToUnit(max.get(), unit);
    }

    @Override
    public Iterable<Measurement> measure() {
        return Arrays.asList(
            new Measurement(() -> (double) count(), Statistic.Count),
            new Measurement(() -> totalTime(TimeUnit.NANOSECONDS), Statistic.TotalTime),
            new Measurement(() -> max(TimeUnit.NANOSECONDS), Statistic.Max)
        );
    }

    private void updateMax(long nanoAmount) {
        while (true) {
            long currentMax = max.get();
            if (currentMax >= nanoAmount) {
                return;
            }
            if (max.compareAndSet(currentMax, nanoAmount)) {
                return;
            }
        }
    }

}
