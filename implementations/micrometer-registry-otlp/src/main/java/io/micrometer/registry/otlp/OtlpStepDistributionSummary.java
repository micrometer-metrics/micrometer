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
import io.micrometer.registry.otlp.internal.Base2ExponentialHistogram;
import io.micrometer.registry.otlp.internal.ExponentialHistogramSnapShot;

import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

class OtlpStepDistributionSummary extends AbstractDistributionSummary implements OtlpHistogramSupport {

    private final HistogramFlavor histogramFlavor;

    private final LongAdder count = new LongAdder();

    private final DoubleAdder total = new DoubleAdder();

    private final OtlpStepTuple2<Long, Double> countTotal;

    private final StepMax max;

    /**
     * Create a new {@code OtlpStepDistributionSummary}.
     * @param id ID
     * @param clock clock
     * @param distributionStatisticConfig distribution statistic configuration
     * @param scale scale
     * @param otlpConfig config for registry
     */
    OtlpStepDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            double scale, OtlpConfig otlpConfig) {
        super(id, scale, OtlpMeterRegistry.getHistogram(clock, distributionStatisticConfig, otlpConfig));
        this.countTotal = new OtlpStepTuple2<>(clock, otlpConfig.step().toMillis(), 0L, 0.0, count::sumThenReset,
                total::sumThenReset);
        this.max = new StepMax(clock, otlpConfig.step().toMillis());
        this.histogramFlavor = OtlpMeterRegistry.histogramFlavor(otlpConfig.histogramFlavor(),
                distributionStatisticConfig);
    }

    @Override
    protected void recordNonNegative(double amount) {
        count.add(1L);
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
    public ExponentialHistogramSnapShot getExponentialHistogramSnapShot() {
        if (histogramFlavor == HistogramFlavor.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM) {
            return ((Base2ExponentialHistogram) histogram).getLatestExponentialHistogramSnapshot();
        }
        return null;
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
        else if (histogram instanceof Base2ExponentialHistogram) {
            histogram.close();
        }
    }

}
