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
package org.springframework.metrics.instrument.prometheus;

import org.springframework.metrics.instrument.DistributionSummary;
import org.springframework.metrics.instrument.Measurement;
import org.springframework.metrics.instrument.Tag;
import org.springframework.metrics.instrument.internal.MeterId;

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
    public Iterable<Measurement> measure() {
        return summary.measure();
    }
}
