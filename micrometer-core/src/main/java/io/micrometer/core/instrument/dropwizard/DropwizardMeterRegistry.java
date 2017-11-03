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
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

import java.lang.ref.WeakReference;
import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

/**
 * @author Jon Schneider
 */
public class DropwizardMeterRegistry extends MeterRegistry {
    private final MetricRegistry registry;
    private final HierarchicalNameMapper nameMapper;
    private final DecimalFormat percentileFormat = new DecimalFormat("#.####");

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
    protected Timer newTimer(Meter.Id id, HistogramConfig histogramConfig) {
        DropwizardTimer timer = new DropwizardTimer(id, registry.timer(hierarchicalName(id)), clock, histogramConfig);

        for (double percentile : histogramConfig.getPercentiles()) {
            gauge(id.getName(), Tags.concat(getConventionTags(id), "percentile", percentileFormat.format(percentile)),
                percentile, p -> timer.percentile(p, getBaseTimeUnit()));
        }

        if(histogramConfig.isPublishingHistogram()) {
            for (Long bucket : histogramConfig.getHistogramBuckets(false)) {
                more().counter(getConventionName(id), Tags.concat(getConventionTags(id), "bucket", Long.toString(bucket)),
                    timer, t -> t.histogramCountAtValue(bucket));
            }
        }

        return timer;
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, HistogramConfig histogramConfig) {
        DropwizardDistributionSummary summary = new DropwizardDistributionSummary(id, clock, registry.histogram(hierarchicalName(id)), histogramConfig);

        for (double percentile : histogramConfig.getPercentiles()) {
            gauge(id.getName(), Tags.concat(getConventionTags(id), "percentile", percentileFormat.format(percentile)),
                percentile, summary::percentile);
        }

        if(histogramConfig.isPublishingHistogram()) {
            for (Long bucket : histogramConfig.getHistogramBuckets(false)) {
                more().counter(getConventionName(id), Tags.concat(getConventionTags(id), "bucket", Long.toString(bucket)),
                    summary, s -> s.histogramCountAtValue(bucket));
            }
        }

        return summary;
    }

    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        LongTaskTimer ltt = new DefaultLongTaskTimer(id, clock);
        registry.register(hierarchicalName(id.withTag(Statistic.ActiveTasks)), (Gauge<Integer>) ltt::activeTasks);
        registry.register(hierarchicalName(id.withTag(Statistic.Duration)), (Gauge<Double>) () -> ltt.duration(TimeUnit.NANOSECONDS));
        return ltt;
    }

    @Override
    protected void newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        measurements.forEach(ms -> registry.register(hierarchicalName(id.withTag(ms.getStatistic())), (Gauge<Double>) ms::getValue));
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.NANOSECONDS;
    }

    private String hierarchicalName(Meter.Id id) {
        return nameMapper.toHierarchicalName(id, config().namingConvention());
    }
}
