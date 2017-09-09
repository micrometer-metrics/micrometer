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
package io.micrometer.core.instrument.spectator;

import com.netflix.spectator.api.Timer;
import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.concurrent.TimeUnit;

public class SpectatorTimer extends AbstractTimer {
    private final com.netflix.spectator.api.Timer timer;
    private final Quantiles quantiles;
    private final Histogram<?> histogram;

    public SpectatorTimer(Id id, Timer timer, Clock clock, Quantiles quantiles, Histogram<?> histogram) {
        super(id, clock);
        this.timer = timer;
        this.quantiles = quantiles;
        this.histogram = histogram;
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        long nanoAmount = unit.toNanos(amount);
        timer.record(nanoAmount, TimeUnit.NANOSECONDS);
        if (quantiles != null)
            quantiles.observe(nanoAmount);
        if (histogram != null)
            histogram.observe(nanoAmount);
    }

    @Override
    public long count() {
        return timer.count();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        // the Spectator Timer contract insists that nanos be returned from totalTime()
        return TimeUtils.nanosToUnit(timer.totalTime(), unit);
    }
}
