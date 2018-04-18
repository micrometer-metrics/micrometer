package io.micrometer.dynatrace;

import com.google.common.collect.ImmutableMap;
import io.micrometer.core.lang.Nullable;
import org.apache.commons.text.StringEscapeUtils;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class DynatraceMetricDefinition {

    /**
     * Subset of mappable units of the custom metric API.
     * @see <a href="https://www.dynatrace.com/support/help/shortlink/api-custom-metrics#put-custom-metric">available units</a>
     */
    enum DynatraceUnit {
        // Time
        NanoSecond, MicroSecond, MilliSecond, Second,
        // Information
        Bit, Byte, KiloByte, KibiByte, MegaByte, MebiByte, GigaByte, GibiByte,
        // Count
        Count;

        private static Map<String, DynatraceUnit> UNITS_MAPPING = ImmutableMap.<String, DynatraceUnit>builder()
            .putAll(Stream.of(DynatraceUnit.values()).collect(Collectors.toMap(k -> k.toString().toLowerCase() + "s", Function.identity())))
            .build();

        static DynatraceUnit fromPlural(final String plural) {
            return UNITS_MAPPING.getOrDefault(plural, null);
        }
    }

    private final String metricId;
    private final String description;
    private final DynatraceUnit unit;
    private final Set<String> dimensions;


    DynatraceMetricDefinition(final String metricId, @Nullable final String description, @Nullable final DynatraceUnit unit, @Nullable final Set<String> dimensions) {
        this.metricId = metricId;
        this.description = description;
        this.unit = unit;
        this.dimensions = dimensions;
    }

    String getMetricId() {
        return metricId;
    }

    String asJson() {
        String body = "{\"displayName\":\"" + (description != null ? StringEscapeUtils.escapeJson(description) : metricId) + "\"";

        if (unit != null)
            body += ",\"unit\":\"" + unit + "\"";

        if (dimensions != null && !dimensions.isEmpty())
            body += ",\"dimensions\":[" + dimensions.stream()
                .map(d -> "\"" + d + "\"")
                .collect(Collectors.joining(",")) + "]";

        body += "}";
        return body;
    }
}
