package io.micrometer.core.instrument.logging;

import io.micrometer.core.instrument.step.StepRegistryConfig;

public interface LoggingRegistryConfig extends StepRegistryConfig {
    LoggingRegistryConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "logging";
    }

    /**
     * Determines whether counters and timers that have no activity in an
     * interval are still logged.
     */
    default boolean logInactive() {
        String v = get(prefix() + ".logInactive");
        return Boolean.parseBoolean(v);
    }
}
