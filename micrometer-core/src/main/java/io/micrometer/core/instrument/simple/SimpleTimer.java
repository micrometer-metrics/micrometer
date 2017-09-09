/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.simple;

import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author Jon Schneider
 */
public class SimpleTimer extends AbstractTimer {
    private LongAdder count = new LongAdder();
    private LongAdder totalTime = new LongAdder();
    private final Quantiles quantiles;
    private final Histogram<?> histogram;

    public SimpleTimer(Id id, Clock clock, Quantiles quantiles, Histogram<?> histogram) {
        super(id, clock);
        this.quantiles = quantiles;
        this.histogram = histogram;
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        if (amount >= 0) {
            count.increment();
            long nanoAmount = TimeUnit.NANOSECONDS.convert(amount, unit);
            totalTime.add(nanoAmount);
            if (quantiles != null)
                quantiles.observe(nanoAmount);
            if (histogram != null)
                histogram.observe(nanoAmount);
        }
    }

    @Override
    public long count() {
        return count.longValue();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return TimeUtils.nanosToUnit(totalTime.doubleValue(), unit);
    }
}
