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

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.histogram.PercentileDistributionSummary;
import com.netflix.spectator.api.histogram.PercentileTimer;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.spectator.atlas.AtlasConfig;
import com.netflix.spectator.atlas.AtlasRegistry;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramGauges;
import io.micrometer.core.instrument.distribution.HistogramSupport;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.step.StepFunctionCounter;
import io.micrometer.core.instrument.step.StepFunctionTimer;
import io.micrometer.core.instrument.util.DoubleFormat;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * @author Jon Schneider
 */
public class AtlasMeterRegistry extends MeterRegistry {

    private final AtlasRegistry registry;

    private final AtlasConfig atlasConfig;

    public AtlasMeterRegistry(AtlasConfig config, Clock clock) {
        super(clock);

        this.atlasConfig = config;

        this.registry = new AtlasRegistry(new com.netflix.spectator.api.Clock() {
            @Override
            public long wallTime() {
                return clock.wallTime();
            }

            @Override
            public long monotonicTime() {
                return clock.monotonicTime();
            }
        }, config);

        // invalid character replacement happens in the spectator-reg-atlas module, so
        // doesn't need
        // to be duplicated here.
        config().namingConvention(new AtlasNamingConvention());

        start();
    }

    public AtlasMeterRegistry(AtlasConfig config) {
        this(config, Clock.SYSTEM);
    }

    public void start() {
        registry.start();
    }

    public void stop() {
        registry.stop();
    }

    @Override
    public void close() {
        stop();
        super.close();
    }

    @Override
    protected io.micrometer.core.instrument.Counter newCounter(Meter.Id id) {
        return new SpectatorCounter(id, registry.counter(spectatorId(id)));
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    protected io.micrometer.core.instrument.DistributionSummary newDistributionSummary(Meter.Id id,
            DistributionStatisticConfig distributionStatisticConfig, double scale) {
        com.netflix.spectator.api.DistributionSummary internalSummary;

        if (distributionStatisticConfig.isPercentileHistogram()) {
            long min = distributionStatisticConfig.getMinimumExpectedValueAsDouble() == null ? 0
                    : distributionStatisticConfig.getMinimumExpectedValueAsDouble().longValue();
            long max = distributionStatisticConfig.getMaximumExpectedValueAsDouble() == null ? Long.MAX_VALUE
                    : distributionStatisticConfig.getMaximumExpectedValueAsDouble().longValue();
            // This doesn't report the normal count/totalTime/max stats, so we treat it as
            // additive
            internalSummary = PercentileDistributionSummary.builder(registry)
                .withId(spectatorId(id))
                .withRange(min, max)
                .build();
        }
        else {
            internalSummary = registry.distributionSummary(spectatorId(id));
        }

        SpectatorDistributionSummary summary = new SpectatorDistributionSummary(id, internalSummary, clock,
                distributionStatisticConfig, scale);

        HistogramGauges.register(summary, this, percentile -> id.getName(),
                percentile -> Tags.concat(id.getTagsAsIterable(), "percentile",
                        DoubleFormat.decimalOrNan(percentile.percentile())),
                ValueAtPercentile::value, bucket -> id.getName(), bucket -> Tags.concat(id.getTagsAsIterable(),
                        "service.level.objective", DoubleFormat.wholeOrDecimal(bucket.bucket())));

        return summary;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector) {
        com.netflix.spectator.api.Timer internalTimer;

        if (distributionStatisticConfig.isPercentileHistogram()) {
            long minNanos = distributionStatisticConfig.getMinimumExpectedValueAsDouble() == null ? 0
                    : distributionStatisticConfig.getMinimumExpectedValueAsDouble().longValue();
            long maxNanos = distributionStatisticConfig.getMaximumExpectedValueAsDouble() == null ? Long.MAX_VALUE
                    : distributionStatisticConfig.getMaximumExpectedValueAsDouble().longValue();
            // This doesn't report the normal count/totalTime/max stats, so we treat it as
            // additive
            internalTimer = PercentileTimer.builder(registry)
                .withId(spectatorId(id))
                .withRange(minNanos, maxNanos, TimeUnit.NANOSECONDS)
                .build();
        }
        else {
            internalTimer = registry.timer(spectatorId(id));
        }

        SpectatorTimer timer = new SpectatorTimer(id, internalTimer, clock, distributionStatisticConfig, pauseDetector,
                getBaseTimeUnit());
        registerHistogramGauges(timer, id, timer.baseTimeUnit());
        return timer;
    }

    private void registerHistogramGauges(HistogramSupport histogramSupport, Meter.Id id, TimeUnit baseTimeUnit) {
        HistogramGauges.register(histogramSupport, this, percentile -> id.getName(),
                percentile -> Tags.concat(id.getTagsAsIterable(), "percentile",
                        DoubleFormat.decimalOrNan(percentile.percentile())),
                percentile -> percentile.value(baseTimeUnit), bucket -> id.getName(),
                bucket -> Tags.concat(id.getTagsAsIterable(), "service.level.objective",
                        DoubleFormat.wholeOrDecimal(bucket.bucket(baseTimeUnit))));
    }

    private Id spectatorId(Meter.Id id) {
        List<com.netflix.spectator.api.Tag> tags = getConventionTags(id).stream()
            .map(t -> new BasicTag(t.getKey(), t.getValue()))
            .collect(toList());
        return registry.createId(getConventionName(id), tags);
    }

    @Override
    protected <T> io.micrometer.core.instrument.Gauge newGauge(Meter.Id id, @Nullable T obj,
            ToDoubleFunction<T> valueFunction) {
        com.netflix.spectator.api.Gauge gauge = new SpectatorToDoubleGauge<>(registry.clock(), spectatorId(id), obj,
                valueFunction);
        registry.register(gauge);
        return new SpectatorGauge(id, gauge);
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        FunctionCounter fc = new StepFunctionCounter<>(id, clock, atlasConfig.step().toMillis(), obj, countFunction);
        PolledMeter.using(registry)
            .withId(spectatorId(id))
            .monitorMonotonicCounter(obj, obj2 -> (long) countFunction.applyAsDouble(obj2));
        return fc;
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction,
            ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        FunctionTimer ft = new StepFunctionTimer<>(id, clock, atlasConfig.step().toMillis(), obj, countFunction,
                totalTimeFunction, totalTimeFunctionUnit, getBaseTimeUnit());
        newMeter(id, Meter.Type.TIMER, ft.measure());
        return ft;
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        SpectatorLongTaskTimer ltt = new SpectatorLongTaskTimer(id,
                com.netflix.spectator.api.patterns.LongTaskTimer.get(registry, spectatorId(id)), clock,
                distributionStatisticConfig);
        registerHistogramGauges(ltt, ltt.getId(), ltt.baseTimeUnit());
        return ltt;
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type,
            Iterable<io.micrometer.core.instrument.Measurement> measurements) {
        Id spectatorId = spectatorId(id);
        com.netflix.spectator.api.AbstractMeter<Id> spectatorMeter = new com.netflix.spectator.api.AbstractMeter<Id>(
                registry.clock(), spectatorId, spectatorId) {
            @Override
            public Iterable<com.netflix.spectator.api.Measurement> measure() {
                return stream(measurements.spliterator(), false).map(m -> {
                    com.netflix.spectator.api.Statistic stat = AtlasUtils.toSpectatorStatistic(m.getStatistic());
                    Id idWithStat = stat == null ? id : id.withTag("statistic", stat.toString());
                    return new com.netflix.spectator.api.Measurement(idWithStat, clock.wallTime(), m.getValue());
                }).collect(toList());
            }
        };
        registry.register(spectatorMeter);
        return new DefaultMeter(id, type, measurements);
    }

    /**
     * @return The underlying Spectator {@link Registry}.
     */
    public Registry getSpectatorRegistry() {
        return registry;
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.builder()
            .expiry(atlasConfig.step())
            .build()
            .merge(DistributionStatisticConfig.DEFAULT);
    }

}
