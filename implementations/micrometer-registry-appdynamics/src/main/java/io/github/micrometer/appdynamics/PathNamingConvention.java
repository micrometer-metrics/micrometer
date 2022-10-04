package io.github.micrometer.appdynamics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.lang.Nullable;

/**
 * @author Ricardo Veloso
 */
public class PathNamingConvention implements NamingConvention {

    private final AppDynamicsConfig config;

    public PathNamingConvention(AppDynamicsConfig config) {
        this.config = config;
    }

    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        return config.prefix() + "|" + name;
    }
}
