/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.appoptics;

import io.micrometer.core.instrument.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppOpticsDtoTest {

    private final AppOpticsDto mockDto = AppOpticsDto.newBuilder()
        .withTime(123)
        .withPeriod(123)
        .withTag("Mock Key", "Mock Value")
        .withMeasurements(Arrays.asList(
            SingleMeasurement.newBuilder()
                .withName("Mock Single Measurement")
                .withValue(1d)
                .withTags(Arrays.asList(new Tag() {
                    @Override
                    public String getKey() {
                        return "Mock Key";
                    }

                    @Override
                    public String getValue() {
                        return "Mock Value";
                    }
                }))
                .build(),
            AggregateMeasurement.newBuilder()
                .withCount(1)
                .withMax(1d)
                .withName("Mock Aggregate Measurement")
                .withSum(1)
                .withTags(Arrays.asList(new Tag() {
                    @Override
                    public String getKey() {
                        return "Mock Key";
                    }

                    @Override
                    public String getValue() {
                        return "Mock Value";
                    }
                }))
                .build()))
        .build();

    private final String expectedJson = "{\"time\":123," +
        "\"period\":123," +
        "\"tags\":{\"MockKey\":\"Mock Value\"}," +
        "\"measurements\":[" +
            "{\"name\":\"MockSingleMeasurement\"," +
            "\"value\":1.0," +
            "\"tags\":{\"MockKey\":\"Mock Value\"}}," +
            "{\"name\":\"MockAggregateMeasurement\"," +
            "\"sum\":1.0," +
            "\"count\":1," +
            "\"max\":1.0," +
            "\"tags\":{\"MockKey\":\"Mock Value\"}}]}";

    @Test
    public void testJson() {

        assertEquals(
            expectedJson,
            mockDto.toJson()
        );
    }

    @Test
    public void testBatching() {

        final List<AppOpticsDto> dtos = mockDto.batch(1);

        assertTrue(dtos.get(0).getMeasurements().size() == 1);
        assertTrue(dtos.get(1).getMeasurements().size() == 1);
        assertEquals(
            mockDto.getMeasurements().get(0),
            dtos.get(0).getMeasurements().get(0));
        assertEquals(
            mockDto.getMeasurements().get(1),
            dtos.get(1).getMeasurements().get(0));
    }
}