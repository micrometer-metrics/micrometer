package io.micrometer.stackdriver;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.core.lang.Nullable;

import java.util.regex.Pattern;

public class StackdriverNamingConvention implements NamingConvention {
    private final NamingConvention nameDelegate;
    private final NamingConvention tagKeyDelegate;

    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_TAG_KEY_LENGTH = 100;

    private static final Pattern NAME_WHITELIST = Pattern.compile("[^\\w./]");
    private static final Pattern TAG_KEY_WHITELIST = Pattern.compile("[^\\w]");

    public StackdriverNamingConvention() {
        this(NamingConvention.slashes, NamingConvention.snakeCase);
    }

    public StackdriverNamingConvention(NamingConvention nameDelegate, NamingConvention tagKeyDelegate) {
        this.nameDelegate = nameDelegate;
        this.tagKeyDelegate = tagKeyDelegate;
    }

    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        return StringUtils.truncate(NAME_WHITELIST.matcher(nameDelegate.name(name, type, baseUnit)).replaceAll("_"),
                MAX_NAME_LENGTH);
    }

    @Override
    public String tagKey(String key) {
        return StringUtils.truncate(TAG_KEY_WHITELIST.matcher(tagKeyDelegate.tagKey(key)).replaceAll("_"),
                MAX_TAG_KEY_LENGTH);
    }
}
