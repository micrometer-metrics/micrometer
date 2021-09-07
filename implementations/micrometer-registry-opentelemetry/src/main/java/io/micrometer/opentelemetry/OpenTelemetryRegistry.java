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
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.*;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;

/**
 * A registry for exporting Micrometer Metrics to an OpenTelemetry registry
 *
 * @author Erin Schnabel
 */
public class OpenTelemetryRegistry extends MeterRegistry {

    final io.opentelemetry.api.metrics.Meter otelMeter;

    public OpenTelemetryRegistry(OpenTelemetryConfig config, Clock clock) {
        super(clock);
        config.requireValid();
        config().onMeterRemoved(this::onMeterRemoved);

        // FIXME
        SdkMeterProvider meterProvider = SdkMeterProvider.builder().build();
        String schemaUrl = "https://localhost/"; // FIXME
        this.otelMeter = meterProvider.get(config.instrumentationName(), config.instrumentationVersion(), schemaUrl);
    }

//    // FIXME
//    public static OpenTelemetry provideOpenTelemetry() {
//        SdkTracerProvider sdkTracerProvider =
//                SdkTracerProvider.builder()
//                        .setSampler(Sampler.alwaysOn())
////                        .addSpanProcessor(SimpleSpanProcessor.create(new LoggingSpanExporter()))
//                        .build();
//
//        return OpenTelemetrySdk.builder()
//                .setTracerProvider(sdkTracerProvider)
//                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
//                .buildAndRegisterGlobal();
//    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> valueFunction) {
        DoubleGaugeBuilder builder = otelMeter.gaugeBuilder(id.getName());
        if ( id.getDescription() != null ) {
            builder.setDescription(id.getDescription());
        }
        if ( id.getBaseUnit() != null ) {
            builder.setUnit(id.getBaseUnit());
        }

        // FIXME What about the attributes?
        builder.buildWithCallback(obdm -> obdm.observe(valueFunction.applyAsDouble(obj)));
        return new OpenTelemetryGauge<T>(id, obj, valueFunction, attributesFrom(id));
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        DoubleCounterBuilder builder = otelMeter.counterBuilder(id.getName()).ofDoubles();
        if ( id.getDescription() != null ) {
            builder.setDescription(id.getDescription());
        }
        if ( id.getBaseUnit() != null ) {
            builder.setUnit(id.getBaseUnit());
        }

        return new OpenTelemetryCounter(id, builder.build(), attributesFrom(id));
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        DoubleHistogramBuilder builder = otelMeter.histogramBuilder(id.getName());
        if ( id.getDescription() != null ) {
            builder.setDescription(id.getDescription());
        }
        builder.setUnit("s");

        return new OpenTelemetryTimer(id, clock, builder.build(), getBaseTimeUnit(), attributesFrom(id));
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
//        DoubleValueObserver.Builder countObserver = otelMeter.doubleValueObserverBuilder(config().namingConvention().name(id.getName(), Meter.Type.COUNTER));
//        DoubleValueObserver.Builder durationObserver = otelMeter.doubleValueObserverBuilder(config().namingConvention().name(id.getName(), Meter.Type.TIMER));

        DoubleGaugeBuilder countObserver = otelMeter.gaugeBuilder(config().namingConvention().name(id.getName(), Meter.Type.COUNTER));
        DoubleGaugeBuilder durationObserver = otelMeter.gaugeBuilder(config().namingConvention().name(id.getName(), Meter.Type.TIMER));

        if ( id.getDescription() != null ) {
            countObserver.setDescription(id.getDescription());
        }
        countObserver.setUnit("1");

        if ( id.getDescription() != null ) {
            durationObserver.setDescription(id.getDescription());
        }
        durationObserver.setUnit("s");

        countObserver.buildWithCallback(obdm -> obdm.observe(countFunction.applyAsLong(obj)));
        durationObserver.buildWithCallback(obdm -> obdm.observe(totalTimeFunction.applyAsDouble(obj)));

        // FIXME What about the attributes
        return new OpenTelemetryFunctionTimer<T>(id, obj, countFunction, totalTimeFunction,
                totalTimeFunctionUnit, getBaseTimeUnit(),
                attributesFrom(id));
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
//        DoubleUpDownSumObserver.Builder builder = otelMeter.doubleUpDownSumObserverBuilder(id.getName());

        DoubleGaugeBuilder builder = otelMeter.gaugeBuilder(id.getName());
        if ( id.getDescription() != null ) {
            builder.setDescription(id.getDescription());
        }
        if ( id.getBaseUnit() != null ) {
            builder.setUnit(id.getBaseUnit());
        }
        builder.buildWithCallback(obdm -> obdm.observe(countFunction.applyAsDouble(obj)));

        // FIXME What about the attributes
        return new OpenTelemetryFunctionCounter<T>(id, obj, countFunction, attributesFrom(id));
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
        // TODO ?
    }

    private Attributes attributesFrom(Meter.Id id) {
        AttributesBuilder builder = Attributes.builder();
        List<Tag> tags = id.getConventionTags(config().namingConvention());
        tags.forEach(x -> builder.put(x.getKey(), x.getValue()));
        return builder.build();
    }
}
