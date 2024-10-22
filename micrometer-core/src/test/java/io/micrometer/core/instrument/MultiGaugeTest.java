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
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.MultiGauge.Row;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

class MultiGaugeTest {

    private static final Color RED = new Color("red", "0xff0000");

    private static final Color GREEN = new Color("green", "0x00ff00");

    private static final Color BLUE = new Color("blue", "0x0000ff");

    private final MeterRegistry registry = new SimpleMeterRegistry();

    private final MultiGauge colorGauges = MultiGauge.builder("colors").register(registry);

    @Test
    void multiGauge() {
        colorGauges.register(Stream.of(RED, GREEN).map(c -> c.toRow(1.0)).collect(toList()));

        assertThat(registry.get("colors").gauges().stream().map(g -> g.getId().getTag("color")))
            .containsExactlyInAnyOrder("red", "green");

        colorGauges.register(Stream.of(RED, BLUE).map(c -> c.toRow(1.0)).collect(toList()));

        assertThat(registry.get("colors").gauges().stream().map(g -> g.getId().getTag("color")))
            .containsExactlyInAnyOrder("red", "blue");
    }

    /**
     * i.e. if you call {@link MultiGauge#register(Iterable)} multiple times, providing a
     * {@link Row} with the same tags, the last row's function definition is the one that
     * is used if overwrite = true.
     */
    @Test
    void overwriteFunctionDefinitions() {
        List<Color> colors = Arrays.asList(RED, GREEN, BLUE);
        colorGauges.register(colors.stream().map(c -> c.toRow(1.0)).collect(toList()));
        colorGauges.register(colors.stream().map(c -> c.toRow(2.0)).collect(toList()), true);

        for (Color color : colors) {
            assertThat(registry.get("colors").tag("color", color.name).gauge().value()).isEqualTo(2);
        }
    }

    @Test
    void dontOverwriteFunctionDefinitions() {
        List<Color> colors = Arrays.asList(RED, GREEN, BLUE);
        colorGauges.register(colors.stream().map(c -> c.toRow(1.0)).collect(toList()));
        colorGauges.register(colors.stream().map(c -> c.toRow(2.0)).collect(toList()));

        for (Color color : colors) {
            assertThat(registry.get("colors").tag("color", color.name).gauge().value()).isEqualTo(1);
        }
    }

    @Test
    void rowGaugesHoldStrongReferences() {
        colorGauges.register(Collections.singletonList(Row.of(Tags.of("color", "red"), () -> 1)));

        System.gc();

        assertThat(registry.get("colors").tag("color", "red").gauge().value()).isEqualTo(1);
    }

    @Test
    void rowGaugesCanTakeSubClassOfNumberSuppliers() {
        final Supplier<Long> supplier = () -> 1L;
        colorGauges.register(Collections.singletonList(Row.of(Tags.of("color", "red"), supplier)));

        assertThat(registry.get("colors").tag("color", "red").gauge().value()).isEqualTo(1);
    }

    @Test
    void overwrite() {
        testOverwrite();
    }

    private void testOverwrite() {
        testOverwrite("my.multi.gauge");
    }

    private void testOverwrite(String mappedMeterName) {
        String testKey1 = "key1";
        AtomicInteger testValue1 = new AtomicInteger(1);
        String testKey2 = "key2";
        AtomicInteger testValue2 = new AtomicInteger(2);

        Map<String, AtomicInteger> map = new ConcurrentHashMap<>();
        map.put(testKey1, testValue1);
        map.put(testKey2, testValue2);

        String meterName = "my.multi.gauge";
        String testTagKey = "tag1";

        MultiGauge gauge = MultiGauge.builder(meterName).register(registry);

        List<Row<?>> rows = map.entrySet()
            .stream()
            .map(row -> Row.of(Tags.of(testTagKey, row.getKey()), row.getValue()))
            .collect(Collectors.toList());
        gauge.register(rows, true);
        assertThat(registry.getMeters()).hasSize(2);
        assertThat(registry.get(mappedMeterName).tag(testTagKey, testKey1).gauge().value())
            .isEqualTo(testValue1.intValue());
        assertThat(registry.get(mappedMeterName).tag(testTagKey, testKey2).gauge().value())
            .isEqualTo(testValue2.intValue());

        testValue1 = new AtomicInteger(100);
        map.put(testKey1, testValue1);
        map.remove(testKey2);

        rows = map.entrySet()
            .stream()
            .map(t -> Row.of(Tags.of(testTagKey, t.getKey()), t.getValue()))
            .collect(Collectors.toList());
        gauge.register(rows, true);

        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.get(mappedMeterName).tag(testTagKey, testKey1).gauge().value())
            .isEqualTo(testValue1.intValue());
    }

    @Test
    void overwriteWithCommonTags() {
        registry.config().commonTags("common1", "1");

        testOverwrite();
    }

    @Test
    void overwriteWithPrefix() {
        registry.config().meterFilter(new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                return id.withName("prefix." + id.getName());
            }
        });

        testOverwrite("prefix.my.multi.gauge");
    }

    private static class Color {

        final String name;

        final String hex;

        Color(String name, String hex) {
            this.name = name;
            this.hex = hex;
        }

        Row<Color> toRow(double frequency) {
            return Row.of(Tags.of("color", name, "hex", hex), this, c -> frequency);
        }

    }

}
