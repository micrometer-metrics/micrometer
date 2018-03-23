package io.micrometer.dynatrace;

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
        assertThat(timeSeries.asJson()).isEqualTo("{\"timeSeriesId\":\"custom:test.metric\",\"dataPoints\":[[12345,1.0]],\"dimensions\":{\"first\":\"one\",\"second\":\"two\"}}");
    }
}