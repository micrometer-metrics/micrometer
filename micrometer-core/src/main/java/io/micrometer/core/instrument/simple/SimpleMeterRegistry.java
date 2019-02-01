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
package io.micrometer.core.instrument.simple;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.cumulative.*;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramGauges;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.step.*;
import io.micrometer.core.lang.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * A minimal meter registry implementation primarily used for tests.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public class SimpleMeterRegistry extends MeterRegistry {
    private final SimpleConfig config;

    public SimpleMeterRegistry() {
        this(SimpleConfig.DEFAULT, Clock.SYSTEM);
    }

    public SimpleMeterRegistry(SimpleConfig config, Clock clock) {
        super(clock);
        this.config = config;
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        DistributionStatisticConfig merged = distributionStatisticConfig.merge(DistributionStatisticConfig.builder()
                .expiry(config.step())
                .build());

        DistributionSummary summary;
        switch (config.mode()) {
            case CUMULATIVE:
                summary = new CumulativeDistributionSummary(id, clock, merged, scale, false);
                break;
            case STEP:
            default:
                summary = new StepDistributionSummary(id, clock, merged, scale, config.step().toMillis(), false);
                break;
        }

        HistogramGauges.registerWithCommonFormat(summary, this);

        return summary;
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return new DefaultMeter(id, type, measurements);
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        DistributionStatisticConfig merged = distributionStatisticConfig.merge(DistributionStatisticConfig.builder()
                .expiry(config.step())
                .build());

        Timer timer;
        switch (config.mode()) {
            case CUMULATIVE:
                timer = new CumulativeTimer(id, clock, merged, pauseDetector, getBaseTimeUnit(), false);
                break;
            case STEP:
            default:
                timer = new StepTimer(id, clock, merged, pauseDetector, getBaseTimeUnit(), config.step().toMillis(), false);
                break;
        }

        HistogramGauges.registerWithCommonFormat(timer, this);

        return timer;
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        return new DefaultGauge<>(id, obj, valueFunction);
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        switch (config.mode()) {
            case CUMULATIVE:
                return new CumulativeCounter(id);
            case STEP:
            default:
                return new StepCounter(id, clock, config.step().toMillis());
        }
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        return new DefaultLongTaskTimer(id, clock);
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        switch (config.mode()) {
            case CUMULATIVE:
                return new CumulativeFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit, getBaseTimeUnit());

            case STEP:
            default:
                return new StepFunctionTimer<>(id, clock, config.step().toMillis(), obj, countFunction, totalTimeFunction, totalTimeFunctionUnit, getBaseTimeUnit());
        }
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        switch (config.mode()) {
            case CUMULATIVE:
                return new CumulativeFunctionCounter<>(id, obj, countFunction);

            case STEP:
            default:
                return new StepFunctionCounter<>(id, clock, config.step().toMillis(), obj, countFunction);
        }
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.builder()
                .expiry(config.step())
                .build()
                .merge(DistributionStatisticConfig.DEFAULT);
    }
}
