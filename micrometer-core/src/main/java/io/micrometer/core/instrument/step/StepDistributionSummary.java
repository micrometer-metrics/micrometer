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
package io.micrometer.core.instrument.step;

import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

import java.util.Arrays;

/**
 * Step-normalized {@link io.micrometer.core.instrument.DistributionSummary}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public class StepDistributionSummary extends AbstractDistributionSummary {
    private final StepLong count;
    private final StepDouble total;
    private final StepDoubleMax max;

    /**
     * Create a new {@code StepDistributionSummary}.
     *
     * @param id                            ID
     * @param clock                         clock
     * @param distributionStatisticConfig   distribution static configuration
     * @param scale                         scale
     * @param stepMillis                    step in milliseconds
     * @param supportsAggregablePercentiles whether it supports aggregable percentiles
     */
    @SuppressWarnings("ConstantConditions")
    public StepDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig, double scale,
                                   long stepMillis, boolean supportsAggregablePercentiles) {
        super(id, clock, distributionStatisticConfig, scale, supportsAggregablePercentiles);
        this.count = new StepLong(clock, stepMillis);
        this.total = new StepDouble(clock, stepMillis);
        this.max = new StepDoubleMax(clock, stepMillis);
    }

    @Override
    protected void recordNonNegative(double amount) {
        count.getCurrent().add(1);
        total.getCurrent().add(amount);
        max.record(amount);
    }

    @Override
    public long count() {
        return count.poll();
    }

    @Override
    public double totalAmount() {
        return total.poll();
    }

    @Override
    public double max() {
        return max.poll();
    }

    @Override
    public Iterable<Measurement> measure() {
        return Arrays.asList(
                new Measurement(() -> (double) count(), Statistic.COUNT),
                new Measurement(this::totalAmount, Statistic.TOTAL),
                new Measurement(this::max, Statistic.MAX)
        );
    }
}
