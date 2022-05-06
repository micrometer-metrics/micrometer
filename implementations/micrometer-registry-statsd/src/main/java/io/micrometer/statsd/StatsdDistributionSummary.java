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

import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.TimeWindowMax;
import reactor.core.publisher.FluxSink;

import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

public class StatsdDistributionSummary extends AbstractDistributionSummary {

    private final LongAdder count = new LongAdder();

    private final DoubleAdder amount = new DoubleAdder();

    private final TimeWindowMax max;

    private final StatsdLineBuilder lineBuilder;

    private final FluxSink<String> sink;

    private volatile boolean shutdown;

    StatsdDistributionSummary(Id id, StatsdLineBuilder lineBuilder, FluxSink<String> sink, Clock clock,
            DistributionStatisticConfig distributionStatisticConfig, double scale) {
        super(id, clock, distributionStatisticConfig, scale, false);
        this.max = new TimeWindowMax(clock, distributionStatisticConfig);
        this.lineBuilder = lineBuilder;
        this.sink = sink;
    }

    @Override
    protected void recordNonNegative(double amount) {
        if (!shutdown && amount >= 0) {
            count.increment();
            this.amount.add(amount);
            max.record(amount);
            sink.next(lineBuilder.histogram(amount));
        }
    }

    @Override
    public long count() {
        return count.longValue();
    }

    @Override
    public double totalAmount() {
        return amount.doubleValue();
    }

    /**
     * The StatsD agent will likely compute max with a different window, so the value may
     * not match what you see here. This value is not exported to the agent, and is only
     * for diagnostic use.
     */
    @Override
    public double max() {
        return max.poll();
    }

    void shutdown() {
        this.shutdown = true;
    }

}
