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

import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.registry.otlp.internal.Base2ExponentialHistogram;
import io.micrometer.registry.otlp.internal.ExponentialHistogramSnapShot;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

class OtlpStepTimer extends AbstractTimer implements OtlpHistogramSupport {

    private final HistogramFlavor histogramFlavor;

    private final LongAdder count = new LongAdder();

    private final LongAdder total = new LongAdder();

    private final OtlpStepTuple2<Long, Long> countTotal;

    private final StepMax max;

    /**
     * Create a new {@code OtlpStepTimer}.
     * @param id ID
     * @param clock clock
     * @param distributionStatisticConfig distribution statistic configuration
     * @param pauseDetector pause detector
     * @param baseTimeUnit base time unit
     * @param otlpConfig config of the registry
     */
    OtlpStepTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector, TimeUnit baseTimeUnit, OtlpConfig otlpConfig) {
        super(id, clock, pauseDetector, otlpConfig.baseTimeUnit(),
                OtlpMeterRegistry.getHistogram(clock, distributionStatisticConfig, otlpConfig, baseTimeUnit));
        countTotal = new OtlpStepTuple2<>(clock, otlpConfig.step().toMillis(), 0L, 0L, count::sumThenReset,
                total::sumThenReset);
        max = new StepMax(clock, otlpConfig.step().toMillis());
        this.histogramFlavor = OtlpMeterRegistry.histogramFlavor(otlpConfig.histogramFlavor(),
                distributionStatisticConfig);
    }

    @Override
    protected void recordNonNegative(final long amount, final TimeUnit unit) {
        final long nanoAmount = (long) TimeUtils.convert(amount, unit, TimeUnit.NANOSECONDS);
        count.add(1L);
        total.add(nanoAmount);
        max.record(nanoAmount);
    }

    @Override
    public long count() {
        return countTotal.poll1();
    }

    @Override
    public double totalTime(final TimeUnit unit) {
        return TimeUtils.nanosToUnit(countTotal.poll2(), unit);
    }

    @Override
    public double max(final TimeUnit unit) {
        return TimeUtils.nanosToUnit(max.poll(), unit);
    }

    /**
     * This is an internal method not meant for general use.
     * <p>
     * Force a rollover of the values returned by a step meter and never roll over again
     * after. See: {@code StepMeter} and {@code StepTimer}
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

    @Override
    public ExponentialHistogramSnapShot getExponentialHistogramSnapShot() {
        if (histogramFlavor == HistogramFlavor.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM) {
            return ((Base2ExponentialHistogram) histogram).getLatestExponentialHistogramSnapshot();
        }
        return null;
    }

}
