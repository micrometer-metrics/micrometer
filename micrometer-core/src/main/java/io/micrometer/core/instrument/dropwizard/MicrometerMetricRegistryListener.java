/**
 * Copyright 2019 Pivotal Software, Inc.
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
package io.micrometer.core.instrument.dropwizard;

import com.codahale.metrics.*;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.function.ToDoubleFunction;

/**
 * Dropwizard {@link MetricRegistryListener} that binds Dropwizard's metrics with Micrometer Meters.
 * Useful for third-party libraries that expose their metrics only through Dropwizard registries.
 * Example use:
 * <code>
 *     metricRegistry.addListener(new MicrometerMetricRegistryListener(meterRegistry));
 * </code>
 *
 * @author Christophe Bornet
 */
public class MicrometerMetricRegistryListener extends MetricRegistryListener.Base {

    private final MeterRegistry registry;
    private String metricPrefix;

    public MicrometerMetricRegistryListener(MeterRegistry registry) {
        this(registry, "dropwizard");
    }

    public MicrometerMetricRegistryListener(MeterRegistry registry, String metricPrefix) {
        this.registry = registry;
        this.metricPrefix = metricPrefix;
    }

    @Override
    public void onGaugeAdded(String name, com.codahale.metrics.Gauge<?> gauge) {
        if (gauge.getValue() instanceof Number) {
            registerGauge(name, gauge, g -> ((Number) g.getValue()).doubleValue());
        }
    }

    @Override
    public void onCounterAdded(String name, Counter counter) {
        // Using a Gauge since Dropwizard's counters are not monotonic.
        registerGauge(name, counter, Counter::getCount);
    }

    @Override
    public void onHistogramAdded(String name, Histogram histogram) {
        registerCounter(name, histogram, Histogram::getCount);
    }

    @Override
    public void onMeterAdded(String name, Meter meter) {
        registerCounter(name, meter, Meter::getCount);
    }

    @Override
    public void onTimerAdded(String name, Timer timer) {
        registerCounter(name, timer, Timer::getCount);
    }

    private <T> void registerGauge(String name, T obj, ToDoubleFunction<T> f) {
        Gauge.builder(String.format("%s.%s", metricPrefix, name), obj, f)
                .register(registry);
    }

    private <T> void registerCounter(String name, T obj, ToDoubleFunction<T> f) {
        FunctionCounter.builder(String.format("%s.%s.count", metricPrefix, name), obj, f)
                .register(registry);
    }

}
