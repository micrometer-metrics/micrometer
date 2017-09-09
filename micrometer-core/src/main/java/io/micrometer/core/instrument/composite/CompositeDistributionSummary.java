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
package io.micrometer.core.instrument.composite;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.noop.NoopDistributionSummary;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class CompositeDistributionSummary extends AbstractMeter implements DistributionSummary, CompositeMeter {
    private final Quantiles quantiles;
    private final Histogram.Builder<?> histogram;

    private final Map<MeterRegistry, DistributionSummary> distributionSummaries =
        Collections.synchronizedMap(new LinkedHashMap<>());

    CompositeDistributionSummary(Meter.Id id, Quantiles quantiles, Histogram.Builder<?> histogram) {
        super(id);
        this.quantiles = quantiles;
        this.histogram = histogram;
    }

    @Override
    public void record(double amount) {
        synchronized (distributionSummaries) {
            distributionSummaries.values().forEach(ds -> ds.record(amount));
        }
    }

    @Override
    public long count() {
        synchronized (distributionSummaries) {
            return distributionSummaries.values().stream().findFirst().orElse(NoopDistributionSummary.INSTANCE).count();
        }
    }

    @Override
    public double totalAmount() {
        synchronized (distributionSummaries) {
            return distributionSummaries.values().stream().findFirst().orElse(NoopDistributionSummary.INSTANCE).totalAmount();
        }
    }

    @Override
    public void add(MeterRegistry registry) {
        synchronized (distributionSummaries) {
            distributionSummaries.put(registry, registry.summary(getId(), histogram, quantiles));
        }
    }

    @Override
    public void remove(MeterRegistry registry) {
        synchronized (distributionSummaries) {
            distributionSummaries.remove(registry);
        }
    }
}
