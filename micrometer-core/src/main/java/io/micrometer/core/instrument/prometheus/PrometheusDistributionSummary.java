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
package io.micrometer.core.instrument.prometheus;

import io.micrometer.core.instrument.util.Meters;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.prometheus.internal.CustomPrometheusSummary;
import io.micrometer.core.instrument.util.MeterId;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Measurement;

import java.util.List;

public class PrometheusDistributionSummary implements DistributionSummary {
    private final MeterId id;
    private final CustomPrometheusSummary.Child summary;

    PrometheusDistributionSummary(MeterId id, CustomPrometheusSummary.Child summary) {
        this.id = id;
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

    @Override
    public String getName() {
        return id.getName();
    }

    @Override
    public Iterable<Tag> getTags() {
        return id.getTags();
    }

    @Override
    public List<Measurement> measure() {
        return summary.measure();
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return Meters.equals(this, o);
    }

    @Override
    public int hashCode() {
        return Meters.hashCode(this);
    }
}
