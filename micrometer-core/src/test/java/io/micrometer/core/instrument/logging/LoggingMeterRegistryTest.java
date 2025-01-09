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
import io.micrometer.core.instrument.binder.BaseUnits;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LoggingMeterRegistry}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Matthieu Borgraeve
 * @author Francois Staudt
 */
class LoggingMeterRegistryTest {

    private final LoggingMeterRegistry registry = new LoggingMeterRegistry();

    private final ConfigurableLoggingRegistryConfig config = new ConfigurableLoggingRegistryConfig();

    private final MockClock clock = new MockClock();

    private final List<String> logs = new ArrayList<>();

    private final LoggingMeterRegistry spyLogRegistry = new LoggingMeterRegistry(config, clock, logs::add);

    @Test
    void defaultMeterIdPrinter() {
        LoggingMeterRegistry registry = LoggingMeterRegistry.builder(LoggingRegistryConfig.DEFAULT).build();
        Counter counter = registry.counter("my.gauage", "tag-1", "tag-2");
        LoggingMeterRegistry.Printer printer = registry.new Printer(counter);

        assertThat(printer.id()).isEqualTo("my.gauage{tag-1=tag-2}");
    }

    @Test
    void providedSinkFromConstructorShouldBeUsed() {
        String expectedString = "my.gauage{tag-1=tag-2} value=1";
        AtomicReference<String> actual = new AtomicReference<>();
        AtomicInteger gaugeValue = new AtomicInteger(1);
        LoggingMeterRegistry registry = new LoggingMeterRegistry(LoggingRegistryConfig.DEFAULT, Clock.SYSTEM,
                actual::set);
        registry.gauge("my.gauage", Tags.of("tag-1", "tag-2"), gaugeValue);

        registry.publish();
        assertThat(actual.get()).isEqualTo(expectedString);
    }

    @Test
    void providedSinkFromConstructorShouldBeUsedWithDefaults() {
        String expectedString = "my.gauage{tag-1=tag-2} value=1";
        AtomicReference<String> actual = new AtomicReference<>();
        AtomicInteger gaugeValue = new AtomicInteger(1);
        LoggingMeterRegistry registry = new LoggingMeterRegistry(actual::set);
        registry.gauge("my.gauage", Tags.of("tag-1", "tag-2"), gaugeValue);

        registry.publish();
        assertThat(actual.get()).isEqualTo(expectedString);
    }

    @Test
    void customMeterIdPrinter() {
        LoggingMeterRegistry registry = LoggingMeterRegistry.builder(LoggingRegistryConfig.DEFAULT)
            .meterIdPrinter(meter -> meter.getId().getName())
            .build();
        Counter counter = registry.counter("my.gauage", "tag-1", "tag-2");
        LoggingMeterRegistry.Printer printer = registry.new Printer(counter);

        assertThat(printer.id()).isEqualTo("my.gauage");
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
        final String expectedResult = "meter.1{} value=0, delta_count=30, throughput=0.5/s";

        Measurement m1 = new Measurement(() -> 0d, Statistic.VALUE);
        Measurement m2 = new Measurement(() -> 30d, Statistic.COUNT);
        Meter meter = Meter.builder("meter.1", Meter.Type.OTHER, List.of(m1, m2)).register(registry);
        LoggingMeterRegistry.Printer printer = registry.new Printer(meter);
        assertThat(registry.writeMeter(meter, printer)).isEqualTo(expectedResult);
    }

    @Test
    void writeMeterMultipleValues() {
        final String expectedResult = "sheepWatch{color=black} value=5 sheep, max=1023 sheep, total=1.1s, delta_count=30 sheep, throughput=0.5 sheep/s";

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
        final String expectedResult = "bus-throughput{} delta_count=300 B, throughput=5 B/s, value=64 B, value=2.125 KiB, value=8 MiB, value=1 GiB";

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
        LoggingMeterRegistry.Printer printer = registry.new Printer(gauge);
        assertThat(printer.value(Double.NaN)).isEqualTo("NaN");
    }

    @Test
    void printerValueWhenGaugeIsInfinityShouldPrintInfinity() {
        registry.gauge("my.gauge", Double.POSITIVE_INFINITY);
        Gauge gauge = registry.find("my.gauge").gauge();
        LoggingMeterRegistry.Printer printer = registry.new Printer(gauge);
        assertThat(printer.value(Double.POSITIVE_INFINITY)).isEqualTo("∞");
    }

    @Test
    void publish_ShouldPrintDeltaCountAndThroughputWithBaseUnit_WhenMeterIsCounter() {
        var counter = Counter.builder("my.counter").baseUnit("sheep").register(spyLogRegistry);
        counter.increment(30);
        clock.add(config.step());
        spyLogRegistry.publish();
        assertThat(logs).containsExactly("my.counter{} delta_count=30 sheep throughput=0.5 sheep/s");
    }

    @Test
    void publish_ShouldPrintDeltaCountAsDecimal_WhenMeterIsCounterAndCountIsDecimal() {
        var counter = spyLogRegistry.counter("my.counter");
        counter.increment(0.5);
        clock.add(config.step());
        spyLogRegistry.publish();
        assertThat(logs).containsExactly("my.counter{} delta_count=0.5 throughput=0.008333/s");
    }

    @Test
    void publish_ShouldPrintDeltaCountAndThroughput_WhenMeterIsTimer() {
        var timer = spyLogRegistry.timer("my.timer");
        IntStream.rangeClosed(1, 30).forEach(t -> timer.record(1, SECONDS));
        clock.add(config.step());
        spyLogRegistry.publish();
        assertThat(logs).containsExactly("my.timer{} delta_count=30 throughput=0.5/s mean=1s max=1s");
    }

    @Test
    void publish_ShouldPrintDeltaCountAndThroughput_WhenMeterIsSummary() {
        var summary = spyLogRegistry.summary("my.summary");
        IntStream.rangeClosed(1, 30).forEach(t -> summary.record(1));
        clock.add(config.step());
        spyLogRegistry.publish();
        assertThat(logs).containsExactly("my.summary{} delta_count=30 throughput=0.5/s mean=1 max=1");
    }

    @Test
    void publish_ShouldPrintDeltaCountAndThroughputWithBaseUnit_WhenMeterIsFunctionCounter() {
        FunctionCounter.builder("my.function-counter", new AtomicDouble(), d -> 30)
            .baseUnit("sheep")
            .register(spyLogRegistry);
        clock.add(config.step());
        spyLogRegistry.publish();
        assertThat(logs).containsExactly("my.function-counter{} delta_count=30 sheep throughput=0.5 sheep/s");
    }

    @Test
    void publish_ShouldPrintDeltaCountAsDecimal_WhenMeterIsFunctionCounterAndCountIsDecimal() {
        spyLogRegistry.more().counter("my.function-counter", emptyList(), new AtomicDouble(), d -> 0.5);
        clock.add(config.step());
        spyLogRegistry.publish();
        assertThat(logs).containsExactly("my.function-counter{} delta_count=0.5 throughput=0.008333/s");
    }

    @Test
    void publish_ShouldPrintDeltaCountAndThroughput_WhenMeterIsFunctionTimer() {
        spyLogRegistry.more().timer("my.function-timer", emptyList(), new AtomicDouble(), d -> 30, d -> 30, SECONDS);
        clock.add(config.step());
        spyLogRegistry.publish();
        assertThat(logs).containsExactly("my.function-timer{} delta_count=30 throughput=0.5/s mean=1s");
    }

    @Test
    void publish_ShouldNotPrintAnything_WhenRegistryIsDisabled() {
        config.set("enabled", "false");
        spyLogRegistry.counter("my.counter").increment();
        clock.add(config.step());
        spyLogRegistry.publish();
        assertThat(spyLogRegistry.getMeters()).hasSize(1);
        assertThat(logs).isEmpty();
    }

    @Test
    void publish_ShouldNotPrintAnything_WhenStepCountIsZeroAndLogsInactiveIsDisabled() {
        spyLogRegistry.counter("my.counter");
        spyLogRegistry.timer("my.timer");
        spyLogRegistry.summary("my.summary");
        spyLogRegistry.more().counter("my.function-counter", emptyList(), new AtomicDouble(), d -> 0);
        spyLogRegistry.more().timer("my.function-timer", emptyList(), new AtomicDouble(), d -> 0, d -> 0, SECONDS);
        clock.add(config.step());
        spyLogRegistry.publish();
        assertThat(spyLogRegistry.getMeters()).hasSize(5);
        assertThat(logs).isEmpty();
    }

    @Test
    void publish_ShouldPrintMetersWithZeroStepCount_WhenLogsInactiveIsEnabled() {
        config.set("logInactive", "true");
        spyLogRegistry.counter("my.counter");
        spyLogRegistry.timer("my.timer");
        spyLogRegistry.summary("my.summary");
        spyLogRegistry.more().counter("my.function-counter", emptyList(), new AtomicDouble(), d -> 0);
        spyLogRegistry.more().timer("my.function-timer", emptyList(), new AtomicDouble(), d -> 0, d -> 0, SECONDS);
        clock.add(config.step());
        spyLogRegistry.publish();
        assertThat(spyLogRegistry.getMeters()).hasSize(5);
        assertThat(logs).containsExactlyInAnyOrder("my.counter{} delta_count=0 throughput=0/s",
                "my.timer{} delta_count=0 throughput=0/s mean= max=",
                "my.summary{} delta_count=0 throughput=0/s mean=0 max=0",
                "my.function-counter{} delta_count=0 throughput=0/s",
                "my.function-timer{} delta_count=0 throughput=0/s mean=");
    }

    private static class ConfigurableLoggingRegistryConfig implements LoggingRegistryConfig {

        private final Map<String, String> keys = new HashMap<>();

        @Override
        public String get(String key) {
            return keys.get(key);
        }

        public void set(String key, String value) {
            keys.put(prefix() + "." + key, value);
        }

    }

}
