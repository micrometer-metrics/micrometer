/**
 * Copyright 2020 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.opentelemetry;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.common.Labels;
import io.opentelemetry.metrics.AsynchronousInstrument;
import io.opentelemetry.metrics.DoubleCounter;
import io.opentelemetry.metrics.DoubleSumObserver;
import io.opentelemetry.metrics.DoubleValueObserver;
import io.opentelemetry.metrics.DoubleValueRecorder;
import io.opentelemetry.metrics.SynchronousInstrument;


/**
 * A registry for exporting Micrometer Metrics to an OpenTelemetry registry
 *
 * @author Erin Schnabel
 */
public class OpenTelemetryRegistry extends MeterRegistry {

    final io.opentelemetry.metrics.Meter otelMeter;

    public OpenTelemetryRegistry(OpenTelemetryConfig config, Clock clock) {
        super(clock);
        config.requireValid();
        config().onMeterRemoved(this::onMeterRemoved);
        this.otelMeter = OpenTelemetry.getMeter(config.instrumentationName(), config.instrumentationVersion());
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> valueFunction) {
        DoubleValueObserver.Builder builder = otelMeter.doubleValueObserverBuilder(id.getName());
        copyAttributes(id, builder);
        if ( id.getBaseUnit() != null ) {
            builder.setUnit(id.getBaseUnit());
        }
        return new OpenTelemetryGauge<T>(id, obj, valueFunction, builder.build());
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        DoubleCounter.Builder builder = otelMeter.doubleCounterBuilder(id.getName());
        copyAttributes(id, builder);
        if ( id.getBaseUnit() != null ) {
            builder.setUnit(id.getBaseUnit());
        }
        return new OpenTelemetryCounter(id, builder.build());
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        DoubleValueRecorder.Builder builder = otelMeter.doubleValueRecorderBuilder(id.getName())
                .setUnit("s");
        copyAttributes(id, builder);
        return new OpenTelemetryTimer(id, clock, builder.build(), TimeUnit.SECONDS);
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        return null;
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return new DefaultMeter(id, type, measurements);
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        // TODO: names must be different
        DoubleSumObserver.Builder countObserver = otelMeter.doubleSumObserverBuilder(id.getName()+".count");
        copyAttributes(id, countObserver);

        DoubleValueObserver.Builder valueObserver = otelMeter.doubleValueObserverBuilder(id.getName()+".total");
        copyAttributes(id, valueObserver);

        return new OpenTelemetryFunctionTimer<T>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit, getBaseTimeUnit(),
                countObserver.build(), valueObserver.build());
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        DoubleSumObserver.Builder builder = otelMeter.doubleSumObserverBuilder(id.getName());
        copyAttributes(id, builder);

        return new OpenTelemetryFunctionCounter<T>(id, obj, countFunction, builder.build());
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.NONE;
    }

    private void onMeterRemoved(Meter meter) {
        // .. ?
    }

    private void copyAttributes(Meter.Id id, AsynchronousInstrument.Builder builder) {
        builder.setConstantLabels(labelsFrom(id));
        if ( id.getDescription() != null ) {
            builder.setDescription(id.getDescription());
        }
    }

    private void copyAttributes(Meter.Id id, SynchronousInstrument.Builder builder) {
        builder.setConstantLabels(labelsFrom(id));
        if ( id.getDescription() != null ) {
            builder.setDescription(id.getDescription());
        }
    }

    private Labels labelsFrom(Meter.Id id) {
        Labels.Builder builder = Labels.newBuilder();
        List<Tag> tags = id.getConventionTags(config().namingConvention());
        tags.forEach(x -> builder.setLabel(x.getKey(), x.getValue()));
        return builder.build();
    }
}
