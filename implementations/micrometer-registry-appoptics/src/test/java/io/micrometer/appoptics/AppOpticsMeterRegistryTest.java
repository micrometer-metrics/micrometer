/*
 * Copyright 2018 VMware, Inc.
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
package io.micrometer.appoptics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.ipc.http.HttpSender;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests for {@link AppOpticsMeterRegistry}.
 *
 * @author Johnny Lim
 * @author Hunter Sherman
 */
class AppOpticsMeterRegistryTest {

    private final AppOpticsConfig config = new AppOpticsConfig() {

        @Override
        public String apiToken() {
            return "fake";
        }

        @Override
        public String get(String key) {
            return null;
        }
    };

    private final AppOpticsConfig configWithFlooring = new AppOpticsConfig() {

        @Override
        public String apiToken() {
            return "fake";
        }

        @Override
        public boolean floorTimes() {
            return true;
        }

        @Override
        public String get(String key) {
            return null;
        }
    };

    private final MockClock clock = new MockClock();

    private final ThreadFactory mockThreadFactory = mock(ThreadFactory.class);

    private final HttpSender mockSender = mock(HttpSender.class);

    private AppOpticsMeterRegistry meterRegistry = new AppOpticsMeterRegistry(config, clock, mockThreadFactory,
            mockSender);

    @Test
    void writeGauge() {
        meterRegistry.gauge("my.gauge", 1d);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge).isPresent()).isTrue();
    }

    @Test
    void writeGaugeShouldDropNanValue() {
        meterRegistry.gauge("my.gauge", Double.NaN);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge).isPresent()).isFalse();
    }

    @Test
    void writeGaugeShouldDropInfiniteValues() {
        meterRegistry.gauge("my.gauge", Double.POSITIVE_INFINITY);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge).isPresent()).isFalse();

        meterRegistry.gauge("my.gauge", Double.NEGATIVE_INFINITY);
        gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge).isPresent()).isFalse();
    }

    @Test
    void writeTimeGauge() {
        AtomicReference<Double> obj = new AtomicReference<>(1d);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeTimeGauge(timeGauge).isPresent()).isTrue();
    }

    @Test
    void writeTimeGaugeShouldDropNanValue() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.NaN);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeTimeGauge(timeGauge).isPresent()).isFalse();
    }

    @Test
    void writeTimeGaugeShouldDropInfiniteValues() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.POSITIVE_INFINITY);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeTimeGauge(timeGauge).isPresent()).isFalse();

        obj = new AtomicReference<>(Double.NEGATIVE_INFINITY);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeTimeGauge(timeGauge).isPresent()).isFalse();
    }

    @Test
    void writeFunctionCounter() {
        FunctionCounter counter = FunctionCounter.builder("myCounter", 1d, Number::doubleValue).register(meterRegistry);
        clock.add(config.step());
        assertThat(meterRegistry.writeFunctionCounter(counter).isPresent()).isTrue();
    }

    @Test
    void writeFunctionCounterShouldDropInfiniteValues() {
        FunctionCounter counter = FunctionCounter.builder("myCounter", Double.POSITIVE_INFINITY, Number::doubleValue)
            .register(meterRegistry);
        clock.add(config.step());
        assertThat(meterRegistry.writeFunctionCounter(counter).isPresent()).isFalse();

        counter = FunctionCounter.builder("myCounter", Double.NEGATIVE_INFINITY, Number::doubleValue)
            .register(meterRegistry);
        clock.add(config.step());
        assertThat(meterRegistry.writeFunctionCounter(counter).isPresent()).isFalse();
    }

    @Test
    void writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten() {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.meterRegistry);
        assertThat(meterRegistry.writeMeter(meter)).isNotPresent();
    }

    @Test
    void writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues() {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        Measurement measurement4 = new Measurement(() -> 1d, Statistic.VALUE);
        Measurement measurement5 = new Measurement(() -> 2d, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3, measurement4,
                measurement5);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.meterRegistry);
        assertThat(meterRegistry.writeMeter(meter)).hasValue(
                "{\"name\":\"my.meter\",\"period\":60,\"value\":1.0,\"tags\":{\"statistic\":\"value\"}},{\"name\":\"my.meter\",\"period\":60,\"value\":2.0,\"tags\":{\"statistic\":\"value\"}}");
    }

    @Test
    void emptyMetersDoNoPosting() {
        meterRegistry.publish();

        verifyNoMoreInteractions(mockSender);
    }

    @Test
    void defaultValueDoesNoFlooring() {
        clock.add(Duration.ofSeconds(63));

        assertThat(meterRegistry.getBodyMeasurementsPrefix())
            .isEqualTo(String.format(AppOpticsMeterRegistry.BODY_MEASUREMENTS_PREFIX, 63));
    }

    @Test
    void flooringRoundsToNearestStep() {
        meterRegistry = new AppOpticsMeterRegistry(configWithFlooring, clock, mockThreadFactory, mockSender);

        clock.add(Duration.ofSeconds(63));

        assertThat(meterRegistry.getBodyMeasurementsPrefix())
            .isEqualTo(String.format(AppOpticsMeterRegistry.BODY_MEASUREMENTS_PREFIX, 60));

        clock.addSeconds(56); // 119

        assertThat(meterRegistry.getBodyMeasurementsPrefix())
            .isEqualTo(String.format(AppOpticsMeterRegistry.BODY_MEASUREMENTS_PREFIX, 60));

        clock.addSeconds(1); // 120

        assertThat(meterRegistry.getBodyMeasurementsPrefix())
            .isEqualTo(String.format(AppOpticsMeterRegistry.BODY_MEASUREMENTS_PREFIX, 120));
    }

}
