package org.springframework.metrics.instrument;

import java.util.ArrayList;

public class Tags {
    public static Iterable<Tag> tagList(String... keyValues) {
        if (keyValues.length % 2 == 1) {
            throw new IllegalArgumentException("size must be even, it is a set of key=value pairs");
        }
        ArrayList<Tag> ts = new ArrayList<>(keyValues.length);
        for (int i = 0; i < keyValues.length; i += 2) {
            ts.add(new ImmutableTag(keyValues[i], keyValues[i + 1]));
        }
        return ts;
    }
}
