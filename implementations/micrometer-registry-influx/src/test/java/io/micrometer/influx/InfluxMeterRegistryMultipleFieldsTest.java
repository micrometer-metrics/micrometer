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
package io.micrometer.influx;

import com.google.common.collect.ImmutableSet;
import io.micrometer.core.instrument.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InfluxMeterRegistry} with multiple fields inline.
 *
 * @author Mariusz Sondecki
 */
class InfluxMeterRegistryMultipleFieldsTest {

    private final InfluxConfig config = InfluxConfig.DEFAULT;
    private final MockClock clock = new MockClock();
    private final InfluxMeterRegistry meterRegistry = new InfluxMeterRegistry.Builder(config)
            .clock(clock)
            .prefixes(ImmutableSet.of("valid.metric1.name", "invalid.metric.name.", "valid.metric2.name"))
            .build();

    @Test
    void testWriteGaugesWithSameTagsAndMultipleFields() {
        //Given
        meterRegistry.gauge("valid.metric1.name.my.field1", Tags.of("foo", "bar"), 1d);
        meterRegistry.gauge("valid.metric1.name.my.field2", Tags.of("foo", "bar"), 2d);

        Gauge gauge1 = meterRegistry.find("valid.metric1.name.my.field1").gauge();
        Gauge gauge2 = meterRegistry.find("valid.metric1.name.my.field2").gauge();

        final InfluxMeterRegistry.MeterKey meterKey1 = meterRegistry.createKeyIfMatched(gauge1);
        final InfluxMeterRegistry.MeterKey meterKey2 = meterRegistry.createKeyIfMatched(gauge2);

        //When
        final List<String> gaugesInfluxLines = meterRegistry.writeMetersAsSingleMultiFieldLine(Arrays.asList(gauge1, gauge2),
                meter -> ((Gauge) meter).value(),
                meterRegistry.getConventionName(meterKey1))
                .collect(Collectors.toList());

        //Then
        assertThat(meterKey1).isEqualTo(meterKey2);
        assertThat(gaugesInfluxLines)
                .hasSize(1)
                .contains("valid_metric1_name,foo=bar,metric_type=gauge my_field1=1,my_field2=2 1");
    }

    @Test
    void testWriteFunctionCountersWithSameTagsAndMultipleFields() {
        FunctionCounter counter1 = FunctionCounter.builder("valid.metric1.name.my.field1", 1d, Number::doubleValue)
                .tags(Tags.of("foo", "bar"))
                .register(meterRegistry);
        FunctionCounter counter2 = FunctionCounter.builder("valid.metric1.name.my.field2", 1d, Number::doubleValue)
                .tags(Tags.of("foo", "bar"))
                .register(meterRegistry);
        clock.add(config.step());

        final InfluxMeterRegistry.MeterKey meterKey1 = meterRegistry.createKeyIfMatched(counter1);
        final InfluxMeterRegistry.MeterKey meterKey2 = meterRegistry.createKeyIfMatched(counter2);

        //When
        final List<String> countersInfluxLines = meterRegistry.writeMetersAsSingleMultiFieldLine(Arrays.asList(counter1, counter2),
                meter -> ((FunctionCounter) meter).count(),
                meterRegistry.getConventionName(meterKey1))
                .collect(Collectors.toList());

        //Then
        assertThat(meterKey1).isEqualTo(meterKey2);
        assertThat(countersInfluxLines)
                .hasSize(1)
                .contains("valid_metric1_name,foo=bar,metric_type=counter my_field1=1,my_field2=1 60001");
    }

    @Test
    void testWriteGaugesWithDifferentTags() {
        //Given
        meterRegistry.gauge("valid.metric1.name.my.field1", Tags.of("foo1", "bar"), 1d);
        meterRegistry.gauge("valid.metric1.name.my.field2", Tags.of("foo2", "bar"), 2d);

        Gauge gauge1 = meterRegistry.find("valid.metric1.name.my.field1").gauge();
        Gauge gauge2 = meterRegistry.find("valid.metric1.name.my.field2").gauge();

        final InfluxMeterRegistry.MeterKey meterKey1 = meterRegistry.createKeyIfMatched(gauge1);
        final InfluxMeterRegistry.MeterKey meterKey2 = meterRegistry.createKeyIfMatched(gauge2);

        //When
        final List<String> gaugesInfluxLinesForKey1 = meterRegistry.writeMetersAsSingleMultiFieldLine(Arrays.asList(gauge1),
                meter -> ((Gauge) meter).value(),
                meterRegistry.getConventionName(meterKey1))
                .collect(Collectors.toList());
        final List<String> gaugesInfluxLinesForKey2 = meterRegistry.writeMetersAsSingleMultiFieldLine(Arrays.asList(gauge2),
                meter -> ((Gauge) meter).value(),
                meterRegistry.getConventionName(meterKey2))
                .collect(Collectors.toList());
        //Then
        assertThat(meterKey1).isNotEqualTo(meterKey2);
        assertThat(gaugesInfluxLinesForKey1)
                .hasSize(1)
                .contains("valid_metric1_name,foo1=bar,metric_type=gauge my_field1=1 1");
        assertThat(gaugesInfluxLinesForKey2)
                .hasSize(1)
                .contains("valid_metric1_name,foo2=bar,metric_type=gauge my_field2=2 1");
    }

    @Test
    void testMatchingGaugesForNotSensiblePrefix() {
        //Given
        meterRegistry.gauge("invalid.metric.name.my.field1", Tags.of("foo", "bar"), 1d);
        meterRegistry.gauge("invalid.metric.name.my.field2", Tags.of("foo", "bar"), 2d);

        Gauge gauge1 = meterRegistry.find("invalid.metric.name.my.field1").gauge();
        Gauge gauge2 = meterRegistry.find("invalid.metric.name.my.field2").gauge();

        //When
        final InfluxMeterRegistry.MeterKey meterKey1 = meterRegistry.createKeyIfMatched(gauge1);
        final InfluxMeterRegistry.MeterKey meterKey2 = meterRegistry.createKeyIfMatched(gauge2);

        //Then
        assertThat(meterKey1).isNull();
        assertThat(meterKey2).isNull();
    }

    @Test
    void testMatchingForDifferentMeters() {
        //Given
        Measurement m1 = new Measurement(() -> 23d, Statistic.VALUE);
        Measurement m2 = new Measurement(() -> 13d, Statistic.VALUE);
        Measurement m3 = new Measurement(() -> 5d, Statistic.TOTAL_TIME);
        Meter meter = Meter.builder("valid.metric1.name.my.custom", Meter.Type.OTHER, Arrays.asList(m1, m2, m3)).register(meterRegistry);

        //When
        final InfluxMeterRegistry.MeterKey meterKey1 = meterRegistry.createKeyIfMatched(meter);

        //Then
        assertThat(meterKey1).isNull();
    }
}
