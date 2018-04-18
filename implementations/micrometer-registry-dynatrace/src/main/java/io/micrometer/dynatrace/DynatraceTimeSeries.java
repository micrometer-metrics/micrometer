package io.micrometer.dynatrace;

import io.micrometer.core.lang.Nullable;

import java.util.Map;
import java.util.stream.Collectors;

class DynatraceTimeSeries {
    private final String metricId;
    private final Map<String, String> dimensions;
    private final long time;
    private final double value;

    DynatraceTimeSeries(final String metricId, final long time, final double value, @Nullable final Map<String, String> dimensions) {
        this.metricId = metricId;
        this.dimensions = dimensions;
        this.time = time;
        this.value = value;
    }

    public String getMetricId() {
        return metricId;
    }

    String asJson() {
        String body = "{\"timeSeriesId\":\"" + metricId + "\"" +
            ",\"dataPoints\":[[" + time + "," + value + "]]";

        if (dimensions != null && !dimensions.isEmpty()) {
            body += ",\"dimensions\":{" +
                dimensions.entrySet().stream()
                    .map(t -> "\"" + t.getKey() + "\":\"" + t.getValue() + "\"")
                    .collect(Collectors.joining(",")) +
                "}";
        }
        body += "}";
        return body;
    }
}
