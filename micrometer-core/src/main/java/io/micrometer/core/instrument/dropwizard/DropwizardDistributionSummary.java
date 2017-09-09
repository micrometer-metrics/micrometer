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
package io.micrometer.core.instrument.dropwizard;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;
import io.micrometer.core.instrument.util.MeterEquivalence;

import java.util.concurrent.atomic.DoubleAdder;

/**
 * @author Jon Schneider
 */
public class DropwizardDistributionSummary extends AbstractMeter implements DistributionSummary {
    private final com.codahale.metrics.Histogram impl;
    private final DoubleAdder totalAmount = new DoubleAdder();
    private final Quantiles quantiles;
    private final Histogram<?> histogram;

    DropwizardDistributionSummary(Id id, com.codahale.metrics.Histogram impl, Quantiles quantiles, Histogram<?> histogram) {
        super(id);
        this.impl = impl;
        this.quantiles = quantiles;
        this.histogram = histogram;
    }

    @Override
    public void record(double amount) {
        if (amount >= 0) {
            impl.update((long) amount);
            totalAmount.add(amount);
            if (quantiles != null)
                quantiles.observe(amount);
            if (histogram != null)
                histogram.observe(amount);
        }
    }

    @Override
    public long count() {
        return impl.getCount();
    }

    @Override
    public double totalAmount() {
        return totalAmount.doubleValue();
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
