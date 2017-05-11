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

import io.prometheus.client.Summary;
import org.springframework.metrics.instrument.Clock;
import org.springframework.metrics.instrument.DistributionSummary;

public class PrometheusDistributionSummary implements DistributionSummary {
    private final String name;
    private Summary.Child summary;

    public PrometheusDistributionSummary(String name, Summary.Child summary) {
        this.name = name;
        this.summary = summary;
    }

    @Override
    public void record(double amount) {
        if (amount >= 0)
            summary.observe(amount);
    }

    @Override
    public long count() {
        return (long) summary.get().count;
    }

    @Override
    public double totalAmount() {
        return (long) summary.get().sum;
    }

    @Override
    public String getName() {
        return name;
    }
}
