package org.springframework.metrics.export.atlas;

import org.springframework.metrics.instrument.TagFormatter;
import org.springframework.util.StringUtils;

public class AtlasTagFormatter implements TagFormatter {
    @Override
    public String formatName(String name) {
        return format(name);
    }

    @Override
    public String formatTagKey(String key) {
        return format(key);
    }

    @Override
    public String formatTagValue(String value) {
        return format(value);
    }

    private String format(String tagKeyOrValue) {
        String sanitized = tagKeyOrValue
                .replaceAll("\\{(\\w+):.+}(?=/|$)", "-$1-") // extract path variable names from regex expressions
                .replaceAll("/", "_")
                .replaceAll("[{}]", "-");
        if (!StringUtils.hasText(sanitized)) {
            sanitized = "none";
        }
        return sanitized;
    }
}
