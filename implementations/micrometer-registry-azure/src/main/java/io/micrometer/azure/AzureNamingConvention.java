package io.micrometer.azure;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.lang.Nullable;

public class AzureNamingConvention implements NamingConvention {
    private final NamingConvention delegate;

    public AzureNamingConvention() {
        this(NamingConvention.dot);
    }

    public AzureNamingConvention(NamingConvention delegate) {
        this.delegate = delegate;
    }

    // Length 1-150 characters.
    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        String conventionName = delegate.name(name, type, baseUnit);
        if (conventionName.length() > 150) {
            conventionName = conventionName.substring(0, 128); // 1
        }
        return conventionName;
    }
}
