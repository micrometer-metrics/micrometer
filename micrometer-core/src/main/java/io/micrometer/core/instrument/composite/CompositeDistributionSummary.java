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
import io.micrometer.core.instrument.HistogramSnapshot;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.noop.NoopDistributionSummary;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CompositeDistributionSummary extends AbstractMeter implements DistributionSummary, CompositeMeter {
    private final Map<MeterRegistry, DistributionSummary> distributionSummaries = new ConcurrentHashMap<>();
    private final HistogramConfig histogramConfig;

    CompositeDistributionSummary(Meter.Id id, HistogramConfig histogramConfig) {
        super(id);
        this.histogramConfig = histogramConfig;
    }

    @Override
    public void record(double amount) {
        distributionSummaries.values().forEach(ds -> ds.record(amount));
    }

    @Override
    public long count() {
        return firstSummary().count();
    }

    @Override
    public double totalAmount() {
        return firstSummary().totalAmount();
    }

    @Override
    public double max() {
        return firstSummary().max();
    }

    @Override
    public double histogramCountAtValue(long value) {
        return firstSummary().histogramCountAtValue(value);
    }

    private DistributionSummary firstSummary() {
        return distributionSummaries.values().stream().findFirst().orElse(new NoopDistributionSummary(getId()));
    }

    @Override
    public double percentile(double percentile) {
        return firstSummary().percentile(percentile);
    }

    @Override
    public HistogramSnapshot takeSnapshot(boolean supportsAggregablePercentiles) {
        return firstSummary().takeSnapshot(supportsAggregablePercentiles);
    }

    @Override
    public void add(MeterRegistry registry) {
        DistributionSummary.Builder builder = DistributionSummary.builder(getId().getName())
            .tags(getId().getTags())
            .description(getId().getDescription())
            .baseUnit(getId().getBaseUnit())
            .publishPercentiles(histogramConfig.getPercentiles())
            .publishPercentileHistogram(histogramConfig.isPercentileHistogram())
            .maximumExpectedValue(histogramConfig.getMaximumExpectedValue())
            .minimumExpectedValue(histogramConfig.getMinimumExpectedValue())
            .histogramBufferLength(histogramConfig.getHistogramBufferLength())
            .histogramExpiry(histogramConfig.getHistogramExpiry())
            .sla(histogramConfig.getSlaBoundaries());

        distributionSummaries.put(registry, builder.register(registry));
    }

    @Override
    public void remove(MeterRegistry registry) {
        distributionSummaries.remove(registry);
    }
}
