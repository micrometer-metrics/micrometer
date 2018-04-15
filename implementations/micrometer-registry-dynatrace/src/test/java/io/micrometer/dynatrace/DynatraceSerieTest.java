package io.micrometer.dynatrace;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DynatraceSerieTest {

    @Test
    void addsDimensionsValuesWhenAvailable() {
        final Map<String, String> dimensions = new HashMap<>();
        dimensions.put("first", "one");
        dimensions.put("second", "two");
        final DynatraceSerie serie = new DynatraceSerie(new DynatraceCustomMetric("custom:test.metric", null, null, null),
            12345, 1, dimensions);
        assertThat(serie.asJson()).isEqualTo("{\"timeSeriesId\":\"custom:test.metric\",\"dataPoints\":[[12345,1.0]],\"dimensions\":{\"first\":\"one\",\"second\":\"two\"}}");
    }
}