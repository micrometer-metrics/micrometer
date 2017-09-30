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
package io.micrometer.core.instrument.dropwizard;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.internal.DefaultFunctionTimer;
import io.micrometer.core.instrument.simple.SimpleLongTaskTimer;
import io.micrometer.core.instrument.stats.hist.Bucket;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.core.instrument.util.TimeUtils;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

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
    protected Counter newCounter(Meter.Id id) {
        return new DropwizardCounter(id, registry.meter(hierarchicalName(id)));
    }

    @Override
    protected <T> io.micrometer.core.instrument.Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        final WeakReference<T> ref = new WeakReference<>(obj);
        Gauge<Double> gauge = () -> {
            T obj2 = ref.get();
            return obj2 != null ? f.applyAsDouble(ref.get()) : Double.NaN;
        };
        registry.register(hierarchicalName(id), gauge);
        return new DropwizardGauge(id, gauge);
    }

    @Override
    protected Timer newTimer(Meter.Id id, Histogram.Builder<?> histogram, Quantiles quantiles) {
        id.setBaseUnit("nanoseconds");
        registerQuantilesGaugeIfNecessary(id, quantiles);
        return new DropwizardTimer(id, registry.timer(hierarchicalName(id)), clock,
            quantiles, registerHistogramCounterIfNecessary(id, histogram));
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, Histogram.Builder<?> histogram, Quantiles quantiles) {
        registerQuantilesGaugeIfNecessary(id, quantiles);
        return new DropwizardDistributionSummary(id, registry.histogram(hierarchicalName(id)),
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
            Histogram<?> hist = histogramBuilder.create(Histogram.Summation.Normal);

            for (Bucket<?> bucket : hist.getBuckets()) {
                more().counter(createId(id.getName(), Tags.concat(id.getTags(), "bucket", bucket.getTagString()), null),
                    bucket, Bucket::getValue);
            }

            return hist;
        }
        return null;
    }

    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        LongTaskTimer ltt = new SimpleLongTaskTimer(id, clock);
        registry.register(hierarchicalName(id) + ".active", (Gauge<Integer>) ltt::activeTasks);
        registry.register(hierarchicalName(id) + ".duration", (Gauge<Double>) () -> ltt.duration(TimeUnit.NANOSECONDS));
        return ltt;
    }

    @Override
    protected <T> Meter newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnits) {
        id.setBaseUnit("nanoseconds");
        FunctionTimer ft = new DefaultFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnits,
            TimeUnit.NANOSECONDS);
        newMeter(id, Meter.Type.Timer, ft.measure());
        return ft;
    }

    @Override
    protected void newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        measurements.forEach(ms -> {
            Meter.Id idWithStatTag = createId(id.getName(), Tags.concat(id.getTags(), "statistic", ms.getStatistic().toString().toLowerCase()),
                id.getDescription(), id.getBaseUnit());
            registry.register(hierarchicalName(idWithStatTag), (Gauge<Double>) ms::getValue);
        });
    }

    @Override
    protected <T> io.micrometer.core.instrument.Gauge newTimeGauge(Meter.Id id, T obj, TimeUnit fUnit, ToDoubleFunction<T> f) {
        id.setBaseUnit("nanoseconds");
        return newGauge(id, obj, obj2 -> TimeUtils.convert(f.applyAsDouble(obj2), fUnit, TimeUnit.NANOSECONDS));
    }

    private String hierarchicalName(Meter.Id id) {
        return nameMapper.toHierarchicalName(id, config().namingConvention());
    }
}
