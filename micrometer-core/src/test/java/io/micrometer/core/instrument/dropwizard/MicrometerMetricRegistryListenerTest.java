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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MicrometerMetricRegistryListenerTest {

    private static final String METRIC_PREFIX = "prefix";
    private static final String METRIC_NAME = "metric";
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private MetricRegistry metricRegistry = new MetricRegistry();

    @BeforeEach
    void setUp() {
        metricRegistry.addListener(new MicrometerMetricRegistryListener(meterRegistry, METRIC_PREFIX));
    }

    @Test
    void shouldBindGauge() {
        AtomicInteger innerVal = new AtomicInteger();
        Gauge<Integer> gauge = innerVal::get;
        metricRegistry.register(METRIC_NAME, gauge);
        innerVal.set(1);
        innerVal.set(42);
        assertEquals(42, meterRegistry.get(String.format("%s.%s", METRIC_PREFIX, METRIC_NAME)).gauge().value());
    }

    @Test
    void shouldBindGaugeWithoutListenerPrefix() {
        metricRegistry.addListener(new MicrometerMetricRegistryListener(meterRegistry));
        AtomicInteger innerVal = new AtomicInteger();
        Gauge<Integer> gauge = innerVal::get;
        metricRegistry.register(METRIC_NAME, gauge);
        innerVal.set(1);
        innerVal.set(42);
        assertEquals(42, meterRegistry.get(String.format("%s.%s", "dropwizard", METRIC_NAME)).gauge().value());
    }

    @Test
    void shouldBindCounter() {
        Counter counter = new Counter();
        metricRegistry.register(METRIC_NAME, counter);
        counter.inc();
        counter.inc(2);
        assertEquals(3, meterRegistry.get(String.format("%s.%s", METRIC_PREFIX, METRIC_NAME)).gauge().value());
    }

    @Test
    void shouldBindHistogram() {
        Histogram histogram = new Histogram(new UniformReservoir());
        metricRegistry.register(METRIC_NAME, histogram);
        histogram.update(1);
        histogram.update(42);
        assertEquals(2, meterRegistry.get(String.format("%s.%s.count", METRIC_PREFIX, METRIC_NAME)).functionCounter().count());
    }

    @Test
    void shouldBindMeter() {
        Meter meter = new Meter();
        metricRegistry.register(METRIC_NAME, meter);
        meter.mark();
        meter.mark(2);
        assertEquals(3, meterRegistry.get(String.format("%s.%s.count", METRIC_PREFIX, METRIC_NAME)).functionCounter().count());
    }

    @Test
    void shouldBindTimer() {
        Timer timer = new Timer();
        metricRegistry.register(METRIC_NAME, timer);
        timer.update(5, TimeUnit.SECONDS);
        timer.update(5, TimeUnit.SECONDS);
        assertEquals(2, meterRegistry.get(String.format("%s.%s.count", METRIC_PREFIX, METRIC_NAME)).functionCounter().count());
    }
}
