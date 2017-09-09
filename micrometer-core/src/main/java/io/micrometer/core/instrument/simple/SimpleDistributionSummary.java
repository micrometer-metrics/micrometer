/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.simple;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;
import io.micrometer.core.instrument.util.MeterEquivalence;

import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

public class SimpleDistributionSummary extends AbstractMeter implements DistributionSummary {
    private LongAdder count = new LongAdder();
    private DoubleAdder amount = new DoubleAdder();
    private final Quantiles quantiles;
    private final Histogram<?> histogram;

    public SimpleDistributionSummary(Id id, Quantiles quantiles, Histogram<?> histogram) {
        super(id);
        this.quantiles = quantiles;
        this.histogram = histogram;
    }

    @Override
    public void record(double amount) {
        if (amount >= 0) {
            count.increment();
            this.amount.add(amount);
            if (quantiles != null)
                quantiles.observe(amount);
            if (histogram != null)
                histogram.observe(amount);
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

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return MeterEquivalence.equals(this, o);
    }

    @Override
    public int hashCode() {
        return MeterEquivalence.hashCode(this);
    }
}
