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
package io.micrometer.atlas;

import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Statistic;
import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

import static java.util.stream.StreamSupport.stream;

public class SpectatorDistributionSummary extends AbstractDistributionSummary {

    private final com.netflix.spectator.api.DistributionSummary summary;

    SpectatorDistributionSummary(Id id, com.netflix.spectator.api.DistributionSummary distributionSummary, Clock clock,
            DistributionStatisticConfig distributionStatisticConfig, double scale) {
        super(id, clock, distributionStatisticConfig, scale, false);
        this.summary = distributionSummary;
    }

    /**
     * @param amount Amount for an event being measured. For this implementation, amount
     * is truncated to a long because the underlying Spectator implementation takes a
     * long.
     */
    @Override
    protected void recordNonNegative(double amount) {
        summary.record((long) amount);
    }

    @Override
    public long count() {
        return summary.count();
    }

    @Override
    public double totalAmount() {
        return summary.totalAmount();
    }

    @Override
    public double max() {
        for (Measurement measurement : summary.measure()) {
            if (stream(measurement.id().tags().spliterator(), false)
                .anyMatch(tag -> tag.key().equals("statistic") && tag.value().equals(Statistic.max.toString()))) {
                return measurement.value();
            }
        }

        return Double.NaN;
    }

}
