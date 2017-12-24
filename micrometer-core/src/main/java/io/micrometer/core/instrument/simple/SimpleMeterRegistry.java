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
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.histogram.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.step.StepCounter;
import io.micrometer.core.instrument.step.StepDistributionSummary;
import io.micrometer.core.instrument.step.StepTimer;
import io.micrometer.core.instrument.util.TimeUtils;

import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * A minimal meter registry implementation primarily used for tests.
 *
 * @author Jon Schneider
 */
public class SimpleMeterRegistry extends MeterRegistry {
    private final DecimalFormat percentileFormat = new DecimalFormat("#.####");
    private final SimpleConfig config;

    public SimpleMeterRegistry() {
        this(SimpleConfig.DEFAULT, Clock.SYSTEM);
    }

    public SimpleMeterRegistry(SimpleConfig config, Clock clock) {
        super(clock);
        this.config = config;
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, HistogramConfig histogramConfig) {
        HistogramConfig merged = histogramConfig.merge(HistogramConfig.builder()
            .histogramExpiry(config.step())
            .build());

        DistributionSummary summary;
        switch (config.mode()) {
            case Cumulative:
                summary = new CumulativeDistributionSummary(id, clock, merged);
                break;
            case Step:
            default:
                summary = new StepDistributionSummary(id, clock, merged, config.step().toMillis());
                break;
        }

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

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return new DefaultMeter(id, type, measurements);
    }

    @Override
    protected Timer newTimer(Meter.Id id, HistogramConfig histogramConfig, PauseDetector pauseDetector) {
        HistogramConfig merged = histogramConfig.merge(HistogramConfig.builder()
            .histogramExpiry(config.step())
            .build());

        Timer timer;
        switch (config.mode()) {
            case Cumulative:
                timer = new CumulativeTimer(id, clock, merged, pauseDetector, getBaseTimeUnit(), config.step().toMillis());
                break;
            case Step:
            default:
                timer = new StepTimer(id, clock, merged, pauseDetector, getBaseTimeUnit(), config.step().toMillis());
                break;
        }

        for (double percentile : histogramConfig.getPercentiles()) {
            gauge(id.getName(), Tags.concat(getConventionTags(id), "percentile", percentileFormat.format(percentile)),
                percentile, p -> timer.percentile(p, getBaseTimeUnit()));
        }

        if(histogramConfig.isPublishingHistogram()) {
            for (Long bucket : histogramConfig.getHistogramBuckets(false)) {
                more().counter(getConventionName(id), Tags.concat(getConventionTags(id), "bucket",
                    percentileFormat.format(TimeUtils.nanosToUnit(bucket, getBaseTimeUnit()))),
                    timer, t -> t.histogramCountAtValue(bucket));
            }
        }

        return timer;
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        return new DefaultGauge<>(id, obj, f);
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        switch (config.mode()) {
            case Cumulative:
                return new CumulativeCounter(id);
            case Step:
            default:
                return new StepCounter(id, clock, config.step().toMillis());
        }
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        return new DefaultLongTaskTimer(id, clock);
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnits) {
        return new CumulativeFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnits, getBaseTimeUnit());
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        return new CumulativeFunctionCounter<>(id, obj, f);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }
}
