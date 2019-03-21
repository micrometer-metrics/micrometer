/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.TimeWindowHistogram;
import io.micrometer.core.instrument.util.MeterEquivalence;
import io.micrometer.core.instrument.util.TimeDecayingMax;
import io.micrometer.core.lang.Nullable;

import java.time.Duration;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

public class PrometheusDistributionSummary extends AbstractDistributionSummary {
    private LongAdder count = new LongAdder();
    private DoubleAdder amount = new DoubleAdder();
    private TimeDecayingMax max;
    private final TimeWindowHistogram percentilesHistogram;

    PrometheusDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        super(id, clock, distributionStatisticConfig, scale);
        this.max = new TimeDecayingMax(clock, distributionStatisticConfig);
        this.percentilesHistogram = new TimeWindowHistogram(clock,
                DistributionStatisticConfig.builder()
                        .expiry(Duration.ofDays(1825)) // effectively never roll over
                        .bufferLength(1)
                        .build()
                        .merge(distributionStatisticConfig));
    }

    @Override
    protected void recordNonNegative(double amount) {
        count.increment();
        this.amount.add(amount);
        max.record(amount);
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
    public boolean equals(@Nullable Object o) {
        return MeterEquivalence.equals(this, o);
    }

    @Override
    public int hashCode() {
        return MeterEquivalence.hashCode(this);
    }

    /**
     * For Prometheus we cannot use the histogram counts from HistogramSnapshot, as it is based on a
     * rolling histogram. Prometheus requires a histogram that accumulates values over the lifetime of the app.
     *
     * @return Cumulative histogram buckets.
     */
    public CountAtBucket[] histogramCounts() {
        return percentilesHistogram.takeSnapshot(0, 0, 0, true).histogramCounts();
    }
}
