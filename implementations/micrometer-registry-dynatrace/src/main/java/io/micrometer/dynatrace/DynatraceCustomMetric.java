package io.micrometer.dynatrace;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.micrometer.core.instrument.Meter;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DynatraceCustomMetric {

    private static Set<String> UNIT_WHITELIST = ImmutableSet.<String>builder()
        // Time
        .add("NanoSecond", "MicroSecond", "MilliSecond", "Second")
        // Information
        .add("Bit", "Byte", "KiloByte", "KibiByte", "MegaByte", "MebiByte", "GigaByte", "GibiByte")
        // Information per time
        //.add("BytePerSecond", "BytePerMinute", "BitPerSecond", "BitPerMinute", "KiloBytePerSecond", "KiloBytePerMinute", "KibiBytePerSecond", "KibiBytePerMinute", "MegaBytePerSecond", "MegaBytePerMinute", "MebiBytePerSecond", "MebiBytePerMinute")
        // Ratio
        //.add("Ratio", "Percent", "Promille")
        // Count
        //.add("Count", "PerSecond", "PerMinute")
        .build();

    private static Map<String, String> UNITS_MAPPING = ImmutableMap.<String, String>builder()
        .putAll(UNIT_WHITELIST.stream().collect(Collectors.toMap(k -> k.toLowerCase() + "s", Function.identity())))
        .build();

    private final Meter.Id id;

    public DynatraceCustomMetric(Meter.Id id) {
        this.id = id;
    }

    String editMetadataBody() {
        String body = "{\"displayName\":\"" + (id.getDescription() != null ? id.getDescription() : id.getName()) + "\"";

        if (id.getBaseUnit() != null) {
            final String mappedUnit = UNIT_WHITELIST.contains(id.getBaseUnit())
                ? id.getBaseUnit()
                : UNITS_MAPPING.get(id.getBaseUnit());
            if (mappedUnit != null)
                body += ",\"unit\":\"" + mappedUnit + "\"";
        }

        if (!id.getTags().isEmpty())
            body += ",\"dimensions\":[" + id.getTags().stream()
                .map(t -> "\"" + t.getKey() + "\"")
                .collect(Collectors.joining(",")) + "]";

        body += "}";
        return body;
    }
}
