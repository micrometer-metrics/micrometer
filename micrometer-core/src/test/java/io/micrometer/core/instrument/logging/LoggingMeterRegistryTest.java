/**
 * Copyright 2018 Pivotal Software, Inc.
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
package io.micrometer.core.instrument.logging;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import org.junit.jupiter.api.Test;

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
    void humanReadableByteCount() {
        LoggingMeterRegistry.Printer printer = registry.new Printer(DistributionSummary.builder("my.summary")
                .baseUnit("bytes")
                .register(registry));

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