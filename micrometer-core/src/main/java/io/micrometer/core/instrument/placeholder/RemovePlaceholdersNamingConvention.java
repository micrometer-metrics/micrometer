package io.micrometer.core.instrument.placeholder;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.core.lang.Nullable;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class RemovePlaceholdersNamingConvention implements NamingConvention {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{.*\\}");

    private final NamingConvention delegate;

    RemovePlaceholdersNamingConvention(NamingConvention delegate) {
        this.delegate = delegate;
    }

    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        String noPlaceholders = PLACEHOLDER.matcher(name).replaceAll("");
        String normalized = Arrays.stream(noPlaceholders.split("\\."))
                .filter(s -> !StringUtils.isEmpty(s))
                .collect(Collectors.joining("."));
        return delegate.name(normalized, type, baseUnit);
    }
}
