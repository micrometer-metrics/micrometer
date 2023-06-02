/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.registry.otlp;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.cumulative.CumulativeTimer;
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;

import java.util.concurrent.TimeUnit;

class OtlpCumulativeTimer extends CumulativeTimer implements StartTimeAwareMeter {

    private final long startTimeNanos;

    OtlpCumulativeTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector, TimeUnit baseTimeUnit) {
        super(id, clock, distributionStatisticConfig, pauseDetector, baseTimeUnit,
                OtlpMeterRegistry.getHistogram(clock, distributionStatisticConfig, AggregationTemporality.CUMULATIVE));
        this.startTimeNanos = TimeUnit.MILLISECONDS.toNanos(clock.wallTime());
    }

    @Override
    public long getStartTimeNanos() {
        return this.startTimeNanos;
    }

}
