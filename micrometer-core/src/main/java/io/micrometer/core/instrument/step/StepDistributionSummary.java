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
package io.micrometer.core.instrument.step;

import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.TimeWindowMax;

import java.util.Arrays;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * Step-normalized {@link io.micrometer.core.instrument.DistributionSummary}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public class StepDistributionSummary extends AbstractDistributionSummary implements StepMeter {

    private final LongAdder count = new LongAdder();

    private final DoubleAdder total = new DoubleAdder();

    private final StepTuple2<Long, Double> countTotal;

    private final TimeWindowMax max;

    /**
     * Create a new {@code StepDistributionSummary}.
     * @param id ID
     * @param clock clock
     * @param distributionStatisticConfig distribution static configuration
     * @param scale scale
     * @param stepMillis step in milliseconds
     * @param supportsAggregablePercentiles whether it supports aggregable percentiles
     */
    public StepDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            double scale, long stepMillis, boolean supportsAggregablePercentiles) {
        super(id, clock, distributionStatisticConfig, scale, supportsAggregablePercentiles);
        this.countTotal = new StepTuple2<>(clock, stepMillis, 0L, 0.0, count::sumThenReset, total::sumThenReset);
        this.max = new TimeWindowMax(clock, distributionStatisticConfig);
    }

    @Override
    protected void recordNonNegative(double amount) {
        count.add(1);
        total.add(amount);
        max.record(amount);
    }

    @Override
    public long count() {
        return countTotal.poll1();
    }

    @Override
    public double totalAmount() {
        return countTotal.poll2();
    }

    @Override
    public double max() {
        return max.poll();
    }

    @Override
    public Iterable<Measurement> measure() {
        return Arrays.asList(new Measurement(() -> (double) count(), Statistic.COUNT),
                new Measurement(this::totalAmount, Statistic.TOTAL), new Measurement(this::max, Statistic.MAX));
    }

    @Override
    public void _closingRollover() {
        countTotal._closingRollover();
    }

}
