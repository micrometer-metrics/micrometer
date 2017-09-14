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
package io.micrometer.prometheus;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.util.MeterEquivalence;
import io.micrometer.prometheus.internal.CustomPrometheusSummary;

public class PrometheusDistributionSummary extends AbstractMeter implements DistributionSummary {
    private final CustomPrometheusSummary.Child summary;

    PrometheusDistributionSummary(Meter.Id id, CustomPrometheusSummary.Child summary) {
        super(id);
        this.summary = summary;
    }

    @Override
    public void record(double amount) {
        if (amount >= 0) {
            summary.observe(amount);
        }
    }

    @Override
    public long count() {
        return summary.count();
    }

    @Override
    public double totalAmount() {
        return summary.sum();
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
