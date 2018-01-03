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
package io.micrometer.core.instrument.step;

import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.util.TimeDecayingMax;

import java.util.Arrays;

public class StepDistributionSummary extends AbstractDistributionSummary {
    private final StepLong count;
    private final StepDouble total;
    private final TimeDecayingMax max;

    public StepDistributionSummary(Id id, Clock clock, HistogramConfig histogramConfig) {
        super(id, clock, histogramConfig);
        this.count = new StepLong(clock, histogramConfig.getHistogramExpiry().toMillis());
        this.total = new StepDouble(clock, histogramConfig.getHistogramExpiry().toMillis());
        this.max = new TimeDecayingMax(clock, histogramConfig);
    }

    @Override
    protected void recordNonNegative(double amount) {
        count.getCurrent().add(1);
        total.getCurrent().add(amount);
        max.record(amount);
    }

    @Override
    public long count() {
        return (long) count.poll();
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
            new Measurement(() -> (double) count(), Statistic.Count),
            new Measurement(this::totalAmount, Statistic.Total),
            new Measurement(this::max, Statistic.Max)
        );
    }
}
