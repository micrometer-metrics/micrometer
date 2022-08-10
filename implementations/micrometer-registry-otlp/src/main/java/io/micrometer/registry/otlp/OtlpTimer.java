/*
 * Copyright 2022 VMware, Inc.
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

import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.util.TimeUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

class OtlpTimer extends AbstractTimer implements StartTimeAwareMeter {

    private final LongAdder count;

    private final LongAdder total;

    private final TimeWindowMax max;

    private final long startTimeNanos;

    OtlpTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector,
            TimeUnit baseTimeUnit) {
        super(id, clock, pauseDetector, baseTimeUnit, newHistogram(clock, distributionStatisticConfig));
        this.startTimeNanos = TimeUnit.MILLISECONDS.toNanos(clock.wallTime());
        this.count = new LongAdder();
        this.total = new LongAdder();
        this.max = new TimeWindowMax(clock, distributionStatisticConfig);

    }

    private static Histogram newHistogram(Clock clock, DistributionStatisticConfig distributionStatisticConfig) {
        if (distributionStatisticConfig.isPublishingPercentiles()) {
            return new TimeWindowPercentileHistogram(clock, distributionStatisticConfig, false);
        }

        if (distributionStatisticConfig.isPublishingHistogram()) {
            // CumulativeTimer doesn't produce monotonic histogram counts; maybe it should
            // Also, we need to customize the histogram behavior to not return cumulative
            // counts across buckets
            return new TimeWindowFixedBoundaryHistogram(clock,
                    DistributionStatisticConfig.builder().expiry(Duration.ofDays(1825)) // effectively
                                                                                        // never
                                                                                        // roll
                                                                                        // over
                            .bufferLength(1).build().merge(distributionStatisticConfig),
                    true, false);
        }

        return NoopHistogram.INSTANCE;
    }

    @Override
    protected void recordNonNegative(long amount, TimeUnit unit) {
        long nanoAmount = (long) TimeUtils.convert(amount, unit, TimeUnit.NANOSECONDS);
        count.increment();
        total.add(nanoAmount);
        max.record(nanoAmount, TimeUnit.NANOSECONDS);
    }

    @Override
    public long getStartTimeNanos() {
        return this.startTimeNanos;
    }

    @Override
    public long count() {
        return count.sum();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return TimeUtils.nanosToUnit(total.sum(), unit);
    }

    @Override
    public double max(TimeUnit unit) {
        return max.poll(unit);
    }

}
