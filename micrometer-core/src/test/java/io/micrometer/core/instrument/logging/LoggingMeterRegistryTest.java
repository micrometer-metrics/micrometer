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
package io.micrometer.core.instrument.logging;

import com.google.common.util.concurrent.AtomicDouble;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.binder.BaseUnits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LoggingMeterRegistry}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Matthieu Borgraeve
 * @author Francois Staudt
 * @author Jonatan Ivanov
 */
class LoggingMeterRegistryTest {

    private final MockClock clock = new MockClock();

    private TestConfig config;

    private LoggingMeterRegistry registry;

    private RecordingLoggingMeterRegistry recordingRegistry;

    @BeforeEach
    void setUp() {
        config = new TestConfig();
        registry = new LoggingMeterRegistry();
        recordingRegistry = new RecordingLoggingMeterRegistry(config, clock);
    }

    @Test
    void defaultMeterIdPrinter() {
        LoggingMeterRegistry registry = LoggingMeterRegistry.builder(LoggingRegistryConfig.DEFAULT).build();
        Counter counter = registry.counter("my.counter", "key1", "test");
        LoggingMeterRegistry.Printer printer = registry.new Printer(counter);

        assertThat(printer.id()).isEqualTo("my.counter{key1=test}");
    }

    @Test
    void providedSinkFromConstructorShouldBeUsed() {
        String expectedString = "my.gauge{key1=test} value=1";
        AtomicReference<String> actual = new AtomicReference<>();
        AtomicInteger gaugeValue = new AtomicInteger(1);
        LoggingMeterRegistry registry = new LoggingMeterRegistry(LoggingRegistryConfig.DEFAULT, Clock.SYSTEM,
                actual::set);
        registry.gauge("my.gauge", Tags.of("key1", "test"), gaugeValue);

        registry.publish();
        assertThat(actual.get()).isEqualTo(expectedString);
    }

    @Test
    void providedSinkFromConstructorShouldBeUsedWithDefaults() {
        String expectedString = "my.gauge{key1=test} value=1";
        AtomicReference<String> actual = new AtomicReference<>();
        AtomicInteger gaugeValue = new AtomicInteger(1);
        LoggingMeterRegistry registry = new LoggingMeterRegistry(actual::set);
        registry.gauge("my.gauge", Tags.of("key1", "test"), gaugeValue);

        registry.publish();
        assertThat(actual.get()).isEqualTo(expectedString);
    }

    @Test
    void customMeterIdPrinter() {
        LoggingMeterRegistry registry = LoggingMeterRegistry.builder(LoggingRegistryConfig.DEFAULT)
            .meterIdPrinter(meter -> meter.getId().getName())
            .build();
        Counter counter = registry.counter("my.counter", "key1", "value");
        LoggingMeterRegistry.Printer printer = registry.new Printer(counter);

        assertThat(printer.id()).isEqualTo("my.counter");
    }

    @Test
    void humanReadableByteCount() {
        LoggingMeterRegistry.Printer printer = registry.new Printer(
                DistributionSummary.builder("my.summary").baseUnit(BaseUnits.BYTES).register(registry));

        assertThat(printer.humanReadableBaseUnit(Double.NaN)).isEqualTo("NaN B");
        assertThat(printer.humanReadableBaseUnit(1.0)).isEqualTo("1 B");
        assertThat(printer.humanReadableBaseUnit(1024)).isEqualTo("1 KiB");
        assertThat(printer.humanReadableBaseUnit(1024 * 1024 * 2.5678976654)).isEqualTo("2.567898 MiB");
    }

    @Test
    void otherUnit() {
        LoggingMeterRegistry.Printer printer = registry.new Printer(
                DistributionSummary.builder("my.summary").baseUnit("things").register(registry));

        assertThat(printer.humanReadableBaseUnit(1.0)).isEqualTo("1 things");
        assertThat(printer.humanReadableBaseUnit(1024)).isEqualTo("1024 things");
    }

    @Test
    void time() {
        LoggingMeterRegistry.Printer printer = registry.new Printer(registry.timer("my.timer"));
        assertThat(printer.time(12345 /* ms */)).isEqualTo("12.345s");
    }

    @Test
    void writeMeterUnitlessValue() {
        String expectedResult = "meter.1{} value=0, delta_count=30, throughput=0.5/s";

        Measurement m1 = new Measurement(() -> 0d, Statistic.VALUE);
        Measurement m2 = new Measurement(() -> 30d, Statistic.COUNT);
        Meter meter = Meter.builder("meter.1", Meter.Type.OTHER, List.of(m1, m2)).register(registry);
        LoggingMeterRegistry.Printer printer = registry.new Printer(meter);
        assertThat(registry.writeMeter(meter, printer)).isEqualTo(expectedResult);
    }

    @Test
    void writeMeterMultipleValues() {
        String expectedResult = "sheepWatch{color=black} value=5 sheep, max=1023 sheep, total=1.1s, delta_count=30 sheep, throughput=0.5 sheep/s";

        Measurement m1 = new Measurement(() -> 5d, Statistic.VALUE);
        Measurement m2 = new Measurement(() -> 1023d, Statistic.MAX);
        Measurement m3 = new Measurement(() -> 1100d, Statistic.TOTAL_TIME);
        Measurement m4 = new Measurement(() -> 30d, Statistic.COUNT);
        Meter meter = Meter.builder("sheepWatch", Meter.Type.OTHER, List.of(m1, m2, m3, m4))
            .tag("color", "black")
            .description("Meter for shepherds.")
            .baseUnit("sheep")
            .register(registry);
        LoggingMeterRegistry.Printer printer = registry.new Printer(meter);
        assertThat(registry.writeMeter(meter, printer)).isEqualTo(expectedResult);
    }

    @Test
    void writeMeterByteValues() {
        String expectedResult = "bus-throughput{} delta_count=300 B, throughput=5 B/s, value=64 B, value=2.125 KiB, value=8 MiB, value=1 GiB";

        Measurement m1 = new Measurement(() -> 300d, Statistic.COUNT);
        Measurement m2 = new Measurement(() -> (double) (1 << 6), Statistic.VALUE);
        Measurement m3 = new Measurement(() -> (double) 0b100010000000, Statistic.VALUE);
        Measurement m4 = new Measurement(() -> (double) (1 << 23), Statistic.VALUE);
        Measurement m5 = new Measurement(() -> (double) (1 << 30), Statistic.VALUE);
        Meter meter = Meter.builder("bus-throughput", Meter.Type.OTHER, Arrays.asList(m1, m2, m3, m4, m5))
            .baseUnit(BaseUnits.BYTES)
            .register(registry);
        LoggingMeterRegistry.Printer printer = registry.new Printer(meter);
        assertThat(registry.writeMeter(meter, printer)).isEqualTo(expectedResult);
    }

    @Test
    void printerValueWhenGaugeIsNaNShouldPrintNaN() {
        registry.gauge("my.gauge", Double.NaN);
        Gauge gauge = registry.find("my.gauge").gauge();
        assertThat(gauge).isNotNull();
        LoggingMeterRegistry.Printer printer = registry.new Printer(gauge);
        assertThat(printer.value(Double.NaN)).isEqualTo("NaN");
    }

    @Test
    void printerValueWhenGaugeIsInfinityShouldPrintInfinity() {
        registry.gauge("my.gauge", Double.POSITIVE_INFINITY);
        Gauge gauge = registry.find("my.gauge").gauge();
        assertThat(gauge).isNotNull();
        LoggingMeterRegistry.Printer printer = registry.new Printer(gauge);
        assertThat(printer.value(Double.POSITIVE_INFINITY)).isEqualTo("∞");
    }

    @Test
    void publishShouldPrintThroughputWithBaseUnitWhenMeterIsCounter() {
        Counter.builder("my.counter").baseUnit("sheep").register(recordingRegistry).increment(30);
        clock.add(config.step());
        recordingRegistry.publish();
        assertThat(recordingRegistry.getLogs())
            .containsExactly("my.counter{} delta_count=30 sheep throughput=0.5 sheep/s");
    }

    @Test
    void publishShouldPrintValueWhenMeterIsGauge() {
        Gauge.builder("my.gauge", () -> 100).baseUnit("C").register(recordingRegistry);
        recordingRegistry.publish();
        assertThat(recordingRegistry.getLogs()).containsExactly("my.gauge{} value=100 C");
    }

    @Test
    void publishShouldPrintThroughputWhenMeterIsTimer() {
        var timer = recordingRegistry.timer("my.timer");
        IntStream.rangeClosed(1, 30).forEach(t -> timer.record(1, SECONDS));
        clock.add(config.step());
        recordingRegistry.publish();
        assertThat(recordingRegistry.getLogs())
            .containsExactly("my.timer{} delta_count=30 throughput=0.5/s mean=1s max=1s");
    }

    @Test
    void publishShouldPrintActiveCountAndDurationWhenMeterIsLongTaskTimer() {
        var timer = recordingRegistry.more().longTaskTimer("my.ltt");
        IntStream.rangeClosed(1, 30).forEach(t -> timer.start());
        clock.add(config.step());
        recordingRegistry.publish();
        assertThat(recordingRegistry.getLogs()).containsExactly("my.ltt{} active=30 duration=30m mean=1m max=1m");
    }

    @Test
    void publishShouldPrintValueWhenMeterIsTimeGauge() {
        recordingRegistry.more().timeGauge("my.time-gauge", Tags.empty(), this, MILLISECONDS, x -> 100);
        clock.add(config.step());
        recordingRegistry.publish();
        assertThat(recordingRegistry.getLogs()).containsExactly("my.time-gauge{} value=0.1s");
    }

    @Test
    void publishShouldPrintThroughputWhenMeterIsSummary() {
        var summary = recordingRegistry.summary("my.summary");
        IntStream.rangeClosed(1, 30).forEach(t -> summary.record(1));
        clock.add(config.step());
        recordingRegistry.publish();
        assertThat(recordingRegistry.getLogs())
            .containsExactly("my.summary{} delta_count=30 throughput=0.5/s mean=1 max=1");
    }

    @Test
    void publishShouldPrintThroughputWithBaseUnitWhenMeterIsFunctionCounter() {
        FunctionCounter.builder("my.function-counter", new AtomicDouble(), d -> 30)
            .baseUnit("sheep")
            .register(recordingRegistry);
        clock.add(config.step());
        recordingRegistry.publish();
        assertThat(recordingRegistry.getLogs())
            .containsExactly("my.function-counter{} delta_count=30 sheep throughput=0.5 sheep/s");
    }

    @Test
    void publishShouldPrintThroughputWhenMeterIsFunctionTimer() {
        recordingRegistry.more().timer("my.function-timer", emptyList(), new AtomicDouble(), d -> 30, d -> 30, SECONDS);
        clock.add(config.step());
        recordingRegistry.publish();
        assertThat(recordingRegistry.getLogs())
            .containsExactly("my.function-timer{} delta_count=30 throughput=0.5/s mean=1s");
    }

    @Test
    void publishShouldPrintValueWhenMeterIsGeneric() {
        Meter.builder("my.meter", Type.OTHER, List.of(new Measurement(() -> 42.0, Statistic.UNKNOWN)))
            .register(recordingRegistry);
        recordingRegistry.publish();
        assertThat(recordingRegistry.getLogs()).containsExactly("my.meter{} unknown=42");
    }

    @Test
    void publishShouldNotPrintAnythingWhenRegistryIsDisabled() {
        config.set("enabled", "false");
        recordingRegistry.counter("my.counter").increment();
        clock.add(config.step());
        recordingRegistry.publish();
        assertThat(recordingRegistry.getMeters()).hasSize(1);
        assertThat(recordingRegistry.getLogs()).isEmpty();
    }

    @Test
    void publishShouldNotPrintAnythingWhenStepCountIsZeroAndLogInactiveIsDisabled() {
        recordingRegistry.counter("my.counter");
        recordingRegistry.timer("my.timer");
        recordingRegistry.summary("my.summary");
        recordingRegistry.more().counter("my.function-counter", emptyList(), new AtomicDouble(), d -> 0);
        recordingRegistry.more().timer("my.function-timer", emptyList(), new AtomicDouble(), d -> 0, d -> 0, SECONDS);
        clock.add(config.step());
        recordingRegistry.publish();
        assertThat(recordingRegistry.getMeters()).hasSize(5);
        assertThat(recordingRegistry.getLogs()).isEmpty();
    }

    @Test
    void publishShouldPrintMetersWithZeroStepCountWhenLogInactiveIsEnabled() {
        config.set("logInactive", "true");
        recordingRegistry.counter("my.counter");
        recordingRegistry.timer("my.timer");
        recordingRegistry.summary("my.summary");
        recordingRegistry.more().counter("my.function-counter", emptyList(), new AtomicDouble(), d -> 0);
        recordingRegistry.more().timer("my.function-timer", emptyList(), new AtomicDouble(), d -> 0, d -> 0, SECONDS);
        clock.add(config.step());
        recordingRegistry.publish();
        assertThat(recordingRegistry.getMeters()).hasSize(5);
        assertThat(recordingRegistry.getLogs()).containsExactlyInAnyOrder("my.counter{} delta_count=0 throughput=0/s",
                "my.timer{} delta_count=0 throughput=0/s mean= max=",
                "my.summary{} delta_count=0 throughput=0/s mean=0 max=0",
                "my.function-counter{} delta_count=0 throughput=0/s",
                "my.function-timer{} delta_count=0 throughput=0/s mean=");
    }

    private static class TestConfig implements LoggingRegistryConfig {

        private final Map<String, String> keys = new HashMap<>();

        @Override
        public String get(String key) {
            return keys.get(key);
        }

        public void set(String key, String value) {
            keys.put(prefix() + "." + key, value);
        }

    }

    private static class RecordingLoggingMeterRegistry extends LoggingMeterRegistry {

        private final List<String> logs;

        RecordingLoggingMeterRegistry(LoggingRegistryConfig config, Clock clock) {
            this(config, clock, new ArrayList<>());
        }

        private RecordingLoggingMeterRegistry(LoggingRegistryConfig config, Clock clock, List<String> logs) {
            super(config, clock, logs::add);
            this.logs = logs;
        }

        List<String> getLogs() {
            return logs;
        }

    }

}
