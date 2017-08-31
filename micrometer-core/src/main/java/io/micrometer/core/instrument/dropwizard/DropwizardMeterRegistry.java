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
import io.micrometer.core.instrument.stats.hist.Bucket;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

/**
 * @author Jon Schneider
 */
public class DropwizardMeterRegistry extends AbstractMeterRegistry {
    private final MetricRegistry registry;
    private final HierarchicalNameMapper nameMapper;

    public DropwizardMeterRegistry(HierarchicalNameMapper nameMapper, Clock clock) {
        super(clock);
        this.registry = new MetricRegistry();
        this.nameMapper = nameMapper;
        this.config().namingConvention(NamingConvention.camelCase);
    }

    public MetricRegistry getDropwizardRegistry() {
        return registry;
    }

    @Override
    protected Counter newCounter(Meter.Id id, String description) {
        return new DropwizardCounter(id, description, registry.meter(nameMapper.toHierarchicalName(id)));
    }

    @Override
    protected <T> io.micrometer.core.instrument.Gauge newGauge(Meter.Id id, String description, ToDoubleFunction<T> f, T obj) {
        final WeakReference<T> ref = new WeakReference<>(obj);
        Gauge<Double> gauge = () -> {
            T obj2 = ref.get();
            return obj2 != null ? f.applyAsDouble(ref.get()) : Double.NaN;
        };
        registry.register(nameMapper.toHierarchicalName(id), gauge);
        return new DropwizardGauge(id, description, gauge);
    }

    @Override
    protected Timer newTimer(Meter.Id id, String description, Histogram.Builder<?> histogram, Quantiles quantiles) {
        registerQuantilesGaugeIfNecessary(id, quantiles);
        return new DropwizardTimer(id, description, registry.timer(nameMapper.toHierarchicalName(id)), clock,
            quantiles, registerHistogramCounterIfNecessary(id, histogram));
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, String description, Histogram.Builder<?> histogram, Quantiles quantiles) {
        registerQuantilesGaugeIfNecessary(id, quantiles);
        return new DropwizardDistributionSummary(id, description, registry.histogram(nameMapper.toHierarchicalName(id)),
            quantiles, registerHistogramCounterIfNecessary(id, histogram));
    }

    private void registerQuantilesGaugeIfNecessary(Meter.Id id, Quantiles quantiles) {
        if (quantiles != null) {
            for (Double q : quantiles.monitored()) {
                gauge(id.getName(), Tags.concat(id.getTags(), "quantile", Double.isNaN(q) ? "NaN" : Double.toString(q)),
                    q, quantiles::get);
            }
        }
    }

    private Histogram<?> registerHistogramCounterIfNecessary(Meter.Id id, Histogram.Builder<?> histogramBuilder) {
        if (histogramBuilder != null) {
            return histogramBuilder
                .bucketListener(bucket -> {
                    more().counter(id.getName(), Tags.concat(id.getTags(), "bucket", bucket.toString()),
                        bucket, Bucket::getValue);
                })
                .create(TimeUnit.NANOSECONDS, Histogram.Type.Normal);
        }
        return null;
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id, String description) {
        LongTaskTimer ltt = new SimpleLongTaskTimer(id, description, clock);
        registry.register(nameMapper.toHierarchicalName(id) + ".active", (Gauge<Integer>) ltt::activeTasks);
        registry.register(nameMapper.toHierarchicalName(id) + ".duration", (Gauge<Long>) ltt::duration);
        return ltt;
    }

    @Override
    protected void newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        measurements.forEach(ms -> registry.register(nameMapper.toHierarchicalName(id), (Gauge<Double>) ms::getValue));
    }
}
