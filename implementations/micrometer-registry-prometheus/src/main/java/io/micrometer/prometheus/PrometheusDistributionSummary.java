/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.prometheus;

import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.CountAtValue;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.histogram.TimeWindowLatencyHistogram;
import io.micrometer.core.instrument.step.StepDouble;
import io.micrometer.core.instrument.util.MeterEquivalence;

import java.time.Duration;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

public class PrometheusDistributionSummary extends AbstractDistributionSummary {
    private LongAdder count = new LongAdder();
    private DoubleAdder amount = new DoubleAdder();
    private StepDouble max;
    private final TimeWindowLatencyHistogram percentilesHistogram;

    PrometheusDistributionSummary(Id id, Clock clock, HistogramConfig histogramConfig, long maxStepMillis) {
        super(id, clock, histogramConfig);
        this.max = new StepDouble(clock, maxStepMillis);
        this.percentilesHistogram = new TimeWindowLatencyHistogram(clock,
            HistogramConfig.builder()
                .histogramExpiry(Duration.ofDays(1825)) // effectively never roll over
                .histogramBufferLength(1)
                .build()
                .merge(histogramConfig));
    }

    @Override
    protected void recordNonNegative(double amount) {
        count.increment();
        this.amount.add(amount);
        max.getCurrent().add(Math.max(amount - max.getCurrent().doubleValue(), 0));
        percentilesHistogram.recordDouble(amount);
    }

    @Override
    public long count() {
        return count.longValue();
    }

    @Override
    public double totalAmount() {
        return amount.doubleValue();
    }

    @Override
    public double max() {
        return max.poll();
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return MeterEquivalence.equals(this, o);
    }

    @Override
    public int hashCode() {
        return MeterEquivalence.hashCode(this);
    }

    /**
     * For Prometheus we cannot use the histogram counts from HistogramSnapshot, as it is based on a
     * rolling histogram. Prometheus requires a histogram that accumulates values over the lifetime of the app.
     */
    public CountAtValue[] percentileBuckets() {
        return percentilesHistogram.takeSnapshot(0, 0, 0, true).histogramCounts();
    }
}
