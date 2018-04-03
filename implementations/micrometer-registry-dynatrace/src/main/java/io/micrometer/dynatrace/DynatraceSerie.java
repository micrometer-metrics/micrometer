package io.micrometer.dynatrace;

import java.util.Map;
import java.util.stream.Collectors;

class DynatraceSerie {
    private final DynatraceCustomMetric metric;
    private final Map<String, String> dimensions;
    private final long time;
    private final double value;

    DynatraceSerie(DynatraceCustomMetric metric, Map<String, String> dimensions, long time, double value) {
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
            body += ",\"dimensions\": {" +
                dimensions.entrySet().stream()
                    .map(t -> "\"" + t.getKey() + "\":\"" + t.getValue() + "\"")
                    .collect(Collectors.joining(",")) +
                "}";
        }
        body += "}";
        return body;
    }
}
