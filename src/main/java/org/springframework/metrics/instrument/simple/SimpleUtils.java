package org.springframework.metrics.instrument.simple;

import org.springframework.metrics.instrument.Meter;
import org.springframework.metrics.instrument.Tag;

public class SimpleUtils {
    public static Tag typeTag(Meter.Type type) {
        return Tag.of("simple.type", type.toString());
    }
}
