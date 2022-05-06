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
package io.micrometer.core.instrument.dropwizard;

import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.TimeWindowMax;

import java.util.concurrent.atomic.DoubleAdder;

/**
 * @author Jon Schneider
 */
public class DropwizardDistributionSummary extends AbstractDistributionSummary {

    private final com.codahale.metrics.Histogram impl;

    private final DoubleAdder totalAmount = new DoubleAdder();

    private final TimeWindowMax max;

    DropwizardDistributionSummary(Id id, Clock clock, com.codahale.metrics.Histogram impl,
            DistributionStatisticConfig distributionStatisticConfig, double scale) {
        super(id, clock, distributionStatisticConfig, scale, false);
        this.impl = impl;
        this.max = new TimeWindowMax(clock, distributionStatisticConfig);
    }

    @Override
    protected void recordNonNegative(double amount) {
        if (amount >= 0) {
            impl.update((long) amount);
            totalAmount.add(amount);
            max.record(amount);
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

    @Override
    public double max() {
        return max.poll();
    }

}
