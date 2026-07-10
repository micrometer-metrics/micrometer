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
package io.micrometer.core.instrument.cumulative;

import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.Histogram;
import io.micrometer.core.instrument.distribution.TimeWindowMax;

import java.util.Arrays;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * Cumulative distribution summary.
 *
 * @author Clint Checketts
 * @author Vladimir Bukhtoyarov
 * @author Jon Schneider
 */
public class CumulativeDistributionSummary extends AbstractDistributionSummary {

    private final LongAdder count;

    private final DoubleAdder total;

    private final TimeWindowMax max;

    @Deprecated
    public CumulativeDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            double scale) {
        this(id, clock, distributionStatisticConfig, scale, false);
    }

    public CumulativeDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            double scale, boolean supportsAggregablePercentiles) {
        this(id, clock, distributionStatisticConfig, scale, AbstractDistributionSummary.defaultHistogram(clock,
                distributionStatisticConfig, supportsAggregablePercentiles));
    }

    /**
     * Creates a {@code CumulativeDistributionSummary} instance.
     * @param id meter ID
     * @param clock clock
     * @param distributionStatisticConfig distribution statistic configuration
     * @param scale scale
     * @param histogram histogram
     * @since 1.11.0
     */
    protected CumulativeDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            double scale, Histogram histogram) {
        super(id, scale, histogram);
        this.count = new LongAdder();
        this.total = new DoubleAdder();
        this.max = new TimeWindowMax(clock, distributionStatisticConfig);
    }

    @Override
    protected void recordNonNegative(double amount) {
        count.increment();
        total.add(amount);
        max.record(amount);
    }

    @Override
    public long count() {
        return count.longValue();
    }

    @Override
    public double totalAmount() {
        return total.sum();
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

}
