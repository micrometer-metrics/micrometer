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

import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

class OtlpStepDistributionSummary extends AbstractDistributionSummary {

    private final LongAdder count = new LongAdder();

    private final DoubleAdder total = new DoubleAdder();

    private final OtlpStepTuple2<Long, Double> countTotal;

    private final StepMax max;

    /**
     * Create a new {@code StepDistributionSummary}.
     * @param id ID
     * @param clock clock
     * @param distributionStatisticConfig distribution static configuration
     * @param scale scale
     * @param stepMillis step in milliseconds
     */
    public OtlpStepDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            double scale, long stepMillis) {
        super(id, scale, OtlpMeterRegistry.getHistogram(clock, distributionStatisticConfig,
                AggregationTemporality.DELTA, stepMillis));
        this.countTotal = new OtlpStepTuple2<>(clock, stepMillis, 0L, 0.0, count::sumThenReset, total::sumThenReset);
        this.max = new StepMax(clock, stepMillis);
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

    /**
     * This is an internal method not meant for general use.
     * <p>
     * Force a rollover of the values returned by a step meter and never roll over again
     * after. See: {@code StepMeter} and {@code StepDistributionSummary}
     */
    void _closingRollover() {
        countTotal._closingRollover();
        max._closingRollover();
        if (histogram instanceof OtlpStepBucketHistogram) { // can be noop
            ((OtlpStepBucketHistogram) histogram)._closingRollover();
        }
    }

}
