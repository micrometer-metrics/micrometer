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
package io.micrometer.core.instrument.simple;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionCounter;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionTimer;
import io.micrometer.core.instrument.step.StepFunctionCounter;
import io.micrometer.core.instrument.step.StepFunctionTimer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SimpleMeterRegistry}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
class SimpleMeterRegistryTest {

    private MockClock clock = new MockClock();

    private SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);

    @Issue("#370")
    @Test
    void serviceLevelObjectivesOnlyNoPercentileHistogram() {
        DistributionSummary summary = DistributionSummary.builder("my.summary")
            .serviceLevelObjectives(1.0, 2)
            .register(registry);

        summary.record(1);

        Timer timer = Timer.builder("my.timer").serviceLevelObjectives(Duration.ofMillis(1)).register(registry);
        timer.record(1, MILLISECONDS);

        Gauge summaryHist1 = registry.get("my.summary.histogram").tags("le", "1").gauge();
        Gauge summaryHist2 = registry.get("my.summary.histogram").tags("le", "2").gauge();
        Gauge timerHist = registry.get("my.timer.histogram").tags("le", "0.001").gauge();

        assertThat(summaryHist1.value()).isEqualTo(1);
        assertThat(summaryHist2.value()).isEqualTo(1);
        assertThat(timerHist.value()).isEqualTo(1);

        clock.add(SimpleConfig.DEFAULT.step());

        assertThat(summaryHist1.value()).isEqualTo(0);
        assertThat(summaryHist2.value()).isEqualTo(0);
        assertThat(timerHist.value()).isEqualTo(0);
    }

    @Test
    void newFunctionTimerWhenCountingModeIsCumulativeShouldReturnCumulativeFunctionTimer() {
        SimpleMeterRegistry registry = createRegistry(CountingMode.CUMULATIVE);
        Meter.Id id = new Meter.Id("some.timer", Tags.empty(), null, null, Meter.Type.TIMER);
        FunctionTimer functionTimer = registry.newFunctionTimer(id, null, (o) -> 0L, (o) -> 0d, TimeUnit.SECONDS);
        assertThat(functionTimer).isInstanceOf(CumulativeFunctionTimer.class);
    }

    @Test
    void newFunctionCounterWhenCountingModeIsCumulativeShouldReturnCumulativeFunctionCounter() {
        SimpleMeterRegistry registry = createRegistry(CountingMode.CUMULATIVE);
        Meter.Id id = new Meter.Id("some.timer", Tags.empty(), null, null, Meter.Type.COUNTER);
        FunctionCounter functionCounter = registry.newFunctionCounter(id, null, (o) -> 0d);
        assertThat(functionCounter).isInstanceOf(CumulativeFunctionCounter.class);
    }

    @Test
    void newFunctionTimerWhenCountingModeIsStepShouldReturnStepFunctionTimer() {
        SimpleMeterRegistry registry = createRegistry(CountingMode.STEP);
        Meter.Id id = new Meter.Id("some.timer", Tags.empty(), null, null, Meter.Type.TIMER);
        FunctionTimer functionTimer = registry.newFunctionTimer(id, null, (o) -> 0L, (o) -> 0d, TimeUnit.SECONDS);
        assertThat(functionTimer).isInstanceOf(StepFunctionTimer.class);
    }

    @Test
    void newFunctionCounterWhenCountingModeIsStepShouldReturnStepFunctionCounter() {
        SimpleMeterRegistry registry = createRegistry(CountingMode.STEP);
        Meter.Id id = new Meter.Id("some.timer", Tags.empty(), null, null, Meter.Type.COUNTER);
        FunctionCounter functionCounter = registry.newFunctionCounter(id, null, (o) -> 0d);
        assertThat(functionCounter).isInstanceOf(StepFunctionCounter.class);
    }

    @Test
    void stringRepresentationOfMetersShouldBeOk() {
        MockClock clock = new MockClock();
        SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);

        AtomicInteger temperature = new AtomicInteger(24);
        Gauge.builder("temperature", () -> temperature).baseUnit("celsius").register(registry);

        Counter correctAnswers = Counter.builder("answers").tag("correct", "true").register(registry);
        correctAnswers.increment();
        correctAnswers.increment();

        Counter incorrectAnswers = Counter.builder("answers").tag("correct", "false").register(registry);
        incorrectAnswers.increment();

        Timer latency = Timer.builder("latency")
            .tag("service", "test")
            .tag("method", "GET")
            .tag("uri", "/api/people")
            .register(registry);

        DistributionSummary requestSize = DistributionSummary.builder("request.size")
            .baseUnit("bytes")
            .register(registry);

        for (int i = 0; i < 10; i++) {
            latency.record(Duration.ofMillis(20 + i * 2));
            requestSize.record(100 + i * 10);
        }

        LongTaskTimer handler = LongTaskTimer.builder("handler").register(registry);
        LongTaskTimer.Sample sample = handler.start();
        clock.add(Duration.ofSeconds(3));

        AtomicLong processingTime = new AtomicLong(300);
        TimeGauge.builder("processing.time", () -> processingTime, MILLISECONDS).register(registry);

        AtomicInteger cacheMisses = new AtomicInteger(42);
        FunctionCounter.builder("cache.miss", cacheMisses, AtomicInteger::doubleValue).register(registry);

        AtomicLong cacheLatency = new AtomicLong(100);
        FunctionTimer.builder("cache.latency", cacheLatency, obj -> 5, AtomicLong::doubleValue, MILLISECONDS)
            .register(registry);

        Meter
            .builder("custom.meter", Meter.Type.OTHER,
                    Arrays.asList(new Measurement(() -> 42d, Statistic.VALUE),
                            new Measurement(() -> 21d, Statistic.UNKNOWN)))
            .register(registry);

        assertThat(registry.getMetersAsString()).isEqualTo("answers(COUNTER)[correct='true']; count=2.0\n"
                + "answers(COUNTER)[correct='false']; count=1.0\n"
                + "cache.latency(TIMER)[]; count=5.0, total_time=0.1 seconds\n" + "cache.miss(COUNTER)[]; count=42.0\n"
                + "custom.meter(OTHER)[]; value=42.0, unknown=21.0\n"
                + "handler(LONG_TASK_TIMER)[]; active_tasks=1.0, duration=3.0 seconds\n"
                + "latency(TIMER)[method='GET', service='test', uri='/api/people']; count=10.0, total_time=0.29 seconds, max=0.038 seconds\n"
                + "processing.time(GAUGE)[]; value=0.3 seconds\n"
                + "request.size(DISTRIBUTION_SUMMARY)[]; count=10.0, total=1450.0 bytes, max=190.0 bytes\n"
                + "temperature(GAUGE)[]; value=24.0 celsius");
        sample.stop();
    }

    @ParameterizedTest
    @MethodSource("getSuppliers")
    void newGaugeWhenSupplierProvidesSubClassOfNumberShouldReportCorrectly(Supplier<? extends Number> supplier) {
        Gauge.builder("temperature", supplier).register(registry);
        assertThat(registry.getMeters()).singleElement().satisfies(meter -> {
            assertThat(meter.getId().getName()).isEqualTo("temperature");
            assertThat(meter.measure()).singleElement()
                .satisfies(measurement -> assertThat(measurement.getValue()).isEqualTo(70));
        });
    }

    @ParameterizedTest
    @MethodSource("getSuppliers")
    void newTimeGaugeWhenSupplierProvidesSubClassOfNumberShouldReportCorrectly(Supplier<? extends Number> supplier) {
        TimeGauge.builder("processing.time", supplier, TimeUnit.SECONDS).register(registry);
        assertThat(registry.getMeters()).singleElement().satisfies(meter -> {
            assertThat(meter.getId().getName()).isEqualTo("processing.time");
            assertThat(meter.getId().getBaseUnit()).isEqualTo("seconds");
            assertThat(meter.measure()).singleElement()
                .satisfies(measurement -> assertThat(measurement.getValue()).isEqualTo(70));
        });
    }

    private static Stream<Supplier<? extends Number>> getSuppliers() {
        return Stream.of((Supplier<Integer>) () -> 70, (Supplier<Double>) () -> 70.0, (Supplier<Long>) () -> 70L,
                (Supplier<AtomicInteger>) () -> new AtomicInteger(70),
                (Supplier<BigInteger>) () -> new BigInteger("70"));
    }

    private SimpleMeterRegistry createRegistry(CountingMode mode) {
        return new SimpleMeterRegistry(new SimpleConfig() {

            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public CountingMode mode() {
                return mode;
            }

        }, clock);
    }

}
