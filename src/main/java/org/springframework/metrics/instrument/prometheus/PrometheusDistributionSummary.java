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
import org.springframework.metrics.instrument.stats.quantile.Quantiles;

public class PrometheusDistributionSummary implements DistributionSummary {
    private final String name;
    private final CustomPrometheusSummary.Child summary;
    private final Quantiles quantiles;

    public PrometheusDistributionSummary(String name, CustomPrometheusSummary.Child summary, Quantiles quantiles) {
        this.name = name;
        this.summary = summary;
        this.quantiles = quantiles;
    }

    @Override
    public void record(double amount) {
        if (amount >= 0) {
            if(quantiles != null)
                quantiles.observe(amount);
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
        return name;
    }
}
