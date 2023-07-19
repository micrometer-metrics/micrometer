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
package io.micrometer.statsd;

import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepDouble;
import io.micrometer.core.instrument.util.TimeUtils;
import reactor.core.publisher.FluxSink;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

public class StatsdTimer extends AbstractTimer {

    private final LongAdder count = new LongAdder();

    private final DoubleAdder totalTime = new DoubleAdder();

    private final StatsdLineBuilder lineBuilder;

    private final FluxSink<String> sink;

    private StepDouble max;

    private volatile boolean shutdown;

    StatsdTimer(Id id, StatsdLineBuilder lineBuilder, FluxSink<String> sink, Clock clock,
            DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector, TimeUnit baseTimeUnit,
            long stepMillis) {
        super(id, clock, distributionStatisticConfig, pauseDetector, baseTimeUnit, false);
        this.max = new StepDouble(clock, stepMillis);
        this.lineBuilder = lineBuilder;
        this.sink = sink;
    }

    @Override
    protected void recordNonNegative(long amount, TimeUnit unit) {
        if (!shutdown && amount >= 0) {
            count.increment();

            double msAmount = TimeUtils.convert(amount, unit, TimeUnit.MILLISECONDS);
            totalTime.add(msAmount);

            // not necessary to ship max, as most StatsD agents calculate this themselves
            max.getCurrent().add(Math.max(msAmount - max.getCurrent().doubleValue(), 0));

            sink.next(lineBuilder.timing(msAmount));
        }
    }

    @Override
    public long count() {
        return count.longValue();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return TimeUtils.convert(totalTime.doubleValue(), TimeUnit.MILLISECONDS, unit);
    }

    /**
     * The StatsD agent will likely compute max with a different window, so the value may
     * not match what you see here. This value is not exported to the agent, and is only
     * for diagnostic use.
     */
    @Override
    public double max(TimeUnit unit) {
        return TimeUtils.convert(max.poll(), TimeUnit.MILLISECONDS, unit);
    }

    void shutdown() {
        this.shutdown = true;
    }

}
