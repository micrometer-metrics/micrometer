package io.micrometer.dynatrace;

import io.micrometer.core.lang.Nullable;

import java.util.Map;
import java.util.stream.Collectors;

class DynatraceSerie {
    private final DynatraceCustomMetric metric;
    private final Map<String, String> dimensions;
    private final long time;
    private final double value;

    DynatraceSerie(final DynatraceCustomMetric metric, final long time, final double value, @Nullable final Map<String, String> dimensions) {
        this.metric = metric;
        this.dimensions = dimensions;
        this.time = time;
        this.value = value;
    }

    DynatraceCustomMetric getMetric() {
        return metric;
    }

    String asJson() {
        String body = "{\"timeSeriesId\":\"" + metric.getMetricId() + "\"" +
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
