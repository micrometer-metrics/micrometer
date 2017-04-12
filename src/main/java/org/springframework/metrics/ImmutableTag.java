package org.springframework.metrics;

public class ImmutableTag implements Tag {
    private String key;
    private String value;

    public ImmutableTag(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getValue() {
        return value;
    }
}
