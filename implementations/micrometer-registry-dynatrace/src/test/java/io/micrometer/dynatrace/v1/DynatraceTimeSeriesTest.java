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
package io.micrometer.dynatrace.v1;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DynatraceTimeSeriesTest {

    @Test
    void addsDimensionsValuesWhenAvailable() {
        final Map<String, String> dimensions = new HashMap<>();
        dimensions.put("first", "one");
        dimensions.put("second", "two");
        final DynatraceTimeSeries timeSeries = new DynatraceTimeSeries("custom:test.metric", 12345, 1, dimensions);
        assertThat(timeSeries.asJson()).isEqualTo(
                "{\"timeseriesId\":\"custom:test.metric\",\"dataPoints\":[[12345,1]],\"dimensions\":{\"first\":\"one\",\"second\":\"two\"}}");
    }

    @Test
    void asJsonShouldEscapeDimensionValue() {
        Map<String, String> dimensions = new HashMap<>();
        dimensions.put("path", "C:\\MyPath");
        dimensions.put("second", "two");
        DynatraceTimeSeries timeSeries = new DynatraceTimeSeries("custom:test.metric", 12345, 1, dimensions);
        assertThat(timeSeries.asJson()).isEqualTo(
                "{\"timeseriesId\":\"custom:test.metric\",\"dataPoints\":[[12345,1]],\"dimensions\":{\"path\":\"C:\\\\MyPath\",\"second\":\"two\"}}");
    }

}
