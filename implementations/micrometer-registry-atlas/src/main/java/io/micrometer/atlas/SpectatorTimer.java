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
package io.micrometer.atlas;

import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.api.Timer;
import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.concurrent.TimeUnit;

import static java.util.stream.StreamSupport.stream;

public class SpectatorTimer extends AbstractTimer {

    private final com.netflix.spectator.api.Timer timer;

    public SpectatorTimer(Id id, Timer timer, Clock clock, DistributionStatisticConfig statsConf,
            PauseDetector pauseDetector, TimeUnit baseTimeUnit) {
        super(id, clock, statsConf, pauseDetector, baseTimeUnit, false);
        this.timer = timer;
    }

    @Override
    protected void recordNonNegative(long amount, TimeUnit unit) {
        timer.record(unit.toNanos(amount), TimeUnit.NANOSECONDS);
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

    @Override
    public double max(TimeUnit unit) {
        for (Measurement measurement : timer.measure()) {
            if (stream(measurement.id().tags().spliterator(), false)
                .anyMatch(tag -> tag.key().equals("statistic") && tag.value().equals(Statistic.max.toString()))) {
                return TimeUtils.secondsToUnit(measurement.value(), unit);
            }
        }

        return Double.NaN;
    }

}
