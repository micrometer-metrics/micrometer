package org.springframework.metrics.instrument;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Key/value pair representing a dimension of a meter used to classify and drill into measurements.
 */
public interface Tag {
    String getKey();

    String getValue();

    static Tag of(String key, String value) {
        return new ImmutableTag(key, value);
    }
}
