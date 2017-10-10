/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CompositeDistributionSummary extends AbstractMeter implements DistributionSummary, CompositeMeter {
    private final Quantiles quantiles;
    private final Histogram.Builder<?> histogram;

    private final Map<MeterRegistry, DistributionSummary> distributionSummaries = new ConcurrentHashMap<>();

    CompositeDistributionSummary(Meter.Id id, Quantiles quantiles, Histogram.Builder<?> histogram) {
        super(id);
        this.quantiles = quantiles;
        this.histogram = histogram;
    }

    @Override
    public void record(double amount) {
        distributionSummaries.values().forEach(ds -> ds.record(amount));
    }

    @Override
    public long count() {
        return distributionSummaries.values().stream().findFirst().orElse(NoopDistributionSummary.INSTANCE).count();
    }

    @Override
    public double totalAmount() {
        return distributionSummaries.values().stream().findFirst().orElse(NoopDistributionSummary.INSTANCE).totalAmount();
    }

    @Override
    public void add(MeterRegistry registry) {
        distributionSummaries.put(registry, registry.summary(getId(), histogram, quantiles));
    }

    @Override
    public void remove(MeterRegistry registry) {
        distributionSummaries.remove(registry);
    }
}
