package org.springframework.metrics.collector;

/**
 * Key/value pair representing a dimension of a meter used to classify and drill into measurements.
 */
public interface Tag {
    String getKey();

    String getValue();
}
