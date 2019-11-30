/**
 * Copyright 2018 Pivotal Software, Inc.
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
package io.micrometer.core.instrument.logging;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.BaseUnits;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LoggingMeterRegistry}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
class LoggingMeterRegistryTest {
    private final LoggingMeterRegistry registry = new LoggingMeterRegistry();

    @Test
    void defaultMeterIdPrinter() {
        LoggingMeterRegistry registry = LoggingMeterRegistry.builder(LoggingRegistryConfig.DEFAULT)
                .build();
        Counter counter = registry.counter("my.gauage", "tag-1", "tag-2");
        LoggingMeterRegistry.Printer printer = registry.new Printer(counter);

        assertThat(printer.id()).isEqualTo("my.gauage{tag-1=tag-2}");
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
        LoggingMeterRegistry.Printer printer = registry.new Printer(DistributionSummary.builder("my.summary")
                .baseUnit(BaseUnits.BYTES)
                .register(registry));

        assertThat(printer.humanReadableBaseUnit(Double.NaN)).isEqualTo("NaN B");
        assertThat(printer.humanReadableBaseUnit(1.0)).isEqualTo("1 B");
        assertThat(printer.humanReadableBaseUnit(1024)).isEqualTo("1 KiB");
        assertThat(printer.humanReadableBaseUnit(1024 * 1024 * 2.5678976654)).isEqualTo("2.567898 MiB");
    }

    @Test
    void otherUnit() {
        LoggingMeterRegistry.Printer printer = registry.new Printer(DistributionSummary.builder("my.summary")
                .baseUnit("things")
                .register(registry));

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
        final String expectedResult = "meter.1{} value=0";

        Measurement m1 = new Measurement(() -> 0d, Statistic.VALUE);
        Meter meter = Meter.builder("meter.1", Meter.Type.OTHER, Collections.singletonList(m1))
                .register(registry);
        LoggingMeterRegistry.Printer printer = registry.new Printer(meter);
        assertThat(registry.writeMeter(meter, printer)).isEqualTo(expectedResult);
    }

    @Test
    void writeMeterMultipleValues() {
        final String expectedResult = "sheepWatch{color=black} value=5 sheep, max=1023 sheep, total=1.1s";

        Measurement m1 = new Measurement(() -> 5d, Statistic.VALUE);
        Measurement m2 = new Measurement(() -> 1023d, Statistic.MAX);
        Measurement m3 = new Measurement(() -> 1100d, Statistic.TOTAL_TIME);
        Meter meter = Meter.builder("sheepWatch", Meter.Type.OTHER, Arrays.asList(m1, m2, m3))
                .tag("color", "black")
                .description("Meter for shepherds.")
                .baseUnit("sheep")
                .register(registry);
        LoggingMeterRegistry.Printer printer = registry.new Printer(meter);
        assertThat(registry.writeMeter(meter, printer)).isEqualTo(expectedResult);
    }

    @Test
    void writeMeterByteValues() {
        final String expectedResult = "bus-throughput{} throughput=5 B/s, value=64 B, value=2.125 KiB, value=8 MiB, value=1 GiB";

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

}
