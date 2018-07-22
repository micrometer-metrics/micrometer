package io.micrometer.kairos;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.StringEscapeUtils;

import java.util.regex.Pattern;

/**
 * @author Anton Ilinchik
 */
public class KairosNamingConvention implements NamingConvention {

    private static final Pattern blacklistedChars = Pattern.compile("[{}():,=\\[\\]]");

    private final NamingConvention delegate;

    public KairosNamingConvention() {
        this(NamingConvention.snakeCase);
    }

    public KairosNamingConvention(NamingConvention delegate) {
        this.delegate = delegate;
    }

    private String format(String name) {
        String normalized = StringEscapeUtils.escapeJson(name);
        return blacklistedChars.matcher(normalized).replaceAll("_");
    }

    @Override
    public String name(String name, Meter.Type type, String baseUnit) {
        return delegate.name(format(name), type, baseUnit);
    }

    @Override
    public String tagKey(String key) {
        return format(key);
    }

    @Override
    public String tagValue(String value) {
        return format(value);
    }

}
