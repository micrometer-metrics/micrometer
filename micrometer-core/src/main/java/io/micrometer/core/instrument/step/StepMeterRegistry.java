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
package io.micrometer.core.instrument.step;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramGauges;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.push.PushMeterRegistry;

import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * Registry that step-normalizes counts and sums to a rate/second over the publishing
 * interval.
 *
 * @author Jon Schneider
 */
public abstract class StepMeterRegistry extends PushMeterRegistry {

    private final StepRegistryConfig config;

    /**
     * Use a different instance of clock that is specific to StepMeterRegistry so that
     * underlying implementations can use either of them to set the timestamps when
     * exporting metrics.
     *
     * A potential issue of updating {@link MeterRegistry#clock} will be forcing the
     * implementations that rely on {@link MeterRegistry#clock} to set the metric
     * timestamp to a future timestamp for the last metric.
     */
    protected final SkewableClock stepRegistryClock;

    public StepMeterRegistry(StepRegistryConfig config, Clock clock) {
        super(config, clock);
        this.config = config;
        this.stepRegistryClock = new SkewableClock(clock);
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        return new DefaultGauge<>(id, obj, valueFunction);
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        return new StepCounter(id, stepRegistryClock, config.step().toMillis());
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        LongTaskTimer ltt = new DefaultLongTaskTimer(id, stepRegistryClock, getBaseTimeUnit(),
                distributionStatisticConfig, false);
        HistogramGauges.registerWithCommonFormat(ltt, this);
        return ltt;
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector) {
        Timer timer = new StepTimer(id, stepRegistryClock, distributionStatisticConfig, pauseDetector,
                getBaseTimeUnit(), this.config.step().toMillis(), false);
        HistogramGauges.registerWithCommonFormat(timer, this);
        return timer;
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id,
            DistributionStatisticConfig distributionStatisticConfig, double scale) {
        DistributionSummary summary = new StepDistributionSummary(id, stepRegistryClock, distributionStatisticConfig,
                scale, config.step().toMillis(), false);
        HistogramGauges.registerWithCommonFormat(summary, this);
        return summary;
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction,
            ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        return new StepFunctionTimer<>(id, stepRegistryClock, config.step().toMillis(), obj, countFunction,
                totalTimeFunction, totalTimeFunctionUnit, getBaseTimeUnit());
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        return new StepFunctionCounter<>(id, stepRegistryClock, config.step().toMillis(), obj, countFunction);
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return new DefaultMeter(id, type, measurements);
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.builder().expiry(config.step()).build()
                .merge(DistributionStatisticConfig.DEFAULT);
    }

    @Override
    public void close() {
        // Move clock to start of next step.
        long now = stepRegistryClock.wallTime();
        long millisUntilNextStep = config.step().toMillis() - now % config.step().toMillis();

        stepRegistryClock.setClockSkew(millisUntilNextStep + 1, TimeUnit.MILLISECONDS);
        super.close();
    }

}
