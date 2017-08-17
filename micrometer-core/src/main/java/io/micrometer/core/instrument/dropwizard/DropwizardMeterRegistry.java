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
package io.micrometer.core.instrument.dropwizard;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleLongTaskTimer;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

import java.lang.ref.WeakReference;
import java.util.function.ToDoubleFunction;

/**
 * @author Jon Schneider
 */
public class DropwizardMeterRegistry extends AbstractMeterRegistry {
    private final MetricRegistry registry;
    private final HierarchicalNameMapper nameMapper;

    public DropwizardMeterRegistry(HierarchicalNameMapper nameMapper, Clock clock, TagFormatter tagFormatter) {
        super(clock, tagFormatter);
        this.registry = new MetricRegistry();
        this.nameMapper = nameMapper;
        this.config().namingConvention(NamingConvention.camelCase);
    }

    public MetricRegistry getDropwizardRegistry() {
        return registry;
    }

    @Override
    protected Counter newCounter(String name, Iterable<Tag> tags) {
        return new DropwizardCounter(name, tags, registry.meter(nameMapper.toHierarchicalName(name, tags)));
    }

    @Override
    protected <T> io.micrometer.core.instrument.Gauge newGauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
        final WeakReference<T> ref = new WeakReference<>(obj);
        Gauge<Double> gauge = () -> f.applyAsDouble(ref.get());
        registry.register(nameMapper.toHierarchicalName(name, tags), gauge);
        return new DropwizardGauge(name, tags, gauge);
    }

    @Override
    protected Timer newTimer(String name, Iterable<Tag> tags, Quantiles quantiles, Histogram<?> histogram) {
        return new DropwizardTimer(name, tags, registry.timer(nameMapper.toHierarchicalName(name, tags)), clock);
    }

    @Override
    protected DistributionSummary newDistributionSummary(String name, Iterable<Tag> tags, Quantiles quantiles, Histogram<?> histogram) {
        // FIXME deal with quantiles, histogram
        return new DropwizardDistributionSummary(name, tags, registry.histogram(nameMapper.toHierarchicalName(name, tags)));
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(String name, Iterable<Tag> tags) {
        LongTaskTimer ltt = new SimpleLongTaskTimer(name, tags, clock);
        registry.register(nameMapper.toHierarchicalName(name, tags) + ".active", (Gauge<Integer>) ltt::activeTasks);
        registry.register(nameMapper.toHierarchicalName(name, tags) + ".duration", (Gauge<Long>) ltt::duration);
        return ltt;
    }

    @Override
    protected void newMeter(String name, Iterable<Tag> tags, Meter.Type type, Iterable<Measurement> measurements) {
        measurements.forEach(ms -> registry.register(nameMapper.toHierarchicalName(name, tags), (Gauge<Double>) ms::getValue));
    }
}
