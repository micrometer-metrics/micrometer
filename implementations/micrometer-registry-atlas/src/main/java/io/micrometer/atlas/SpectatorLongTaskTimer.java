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

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.concurrent.TimeUnit;

public class SpectatorLongTaskTimer extends DefaultLongTaskTimer implements LongTaskTimer {

    private final com.netflix.spectator.api.LongTaskTimer timer;

    SpectatorLongTaskTimer(Meter.Id id, com.netflix.spectator.api.LongTaskTimer timer, Clock clock,
            DistributionStatisticConfig distributionStatisticConfig) {
        super(id, clock, TimeUnit.NANOSECONDS, distributionStatisticConfig, true);
        this.timer = timer;
    }

    @Override
    public Sample start() {
        return new SpectatorSample(super.start(), timer.start());
    }

    @Override
    public double duration(TimeUnit unit) {
        return TimeUtils.nanosToUnit(timer.duration(), unit);
    }

    @Override
    public int activeTasks() {
        return timer.activeTasks();
    }

    class SpectatorSample extends Sample {

        private final Sample delegate;

        private final long taskId;

        public SpectatorSample(Sample delegate, long taskId) {
            this.delegate = delegate;
            this.taskId = taskId;
        }

        @Override
        public long stop() {
            delegate.stop();
            return timer.stop(taskId);
        }

        @Override
        public double duration(TimeUnit unit) {
            return TimeUtils.nanosToUnit(timer.duration(taskId), unit);
        }

    }

}
