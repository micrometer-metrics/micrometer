package org.springframework.metrics.instrument;

/**
 * Many metrics backends have constraints on valid characters that may appear
 * in a tag key/value or metric name. While it is recommended to choose tag
 * keys/values that are absent special characters that are invalid on any
 * common metrics backend, sometimes this is hard to avoid (as in the format
 * of the URI template for parameterized URIs like /api/person/{id} emanating
 * from Spring Web).
 *
 * @author Jon Schneider
 */
public interface TagFormatter {
    default String formatName(String name) { return name; }
    default String formatTagKey(String key) { return key; }
    default String formatTagValue(String value) { return value; }
}
