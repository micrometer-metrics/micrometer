package io.micrometer.core.instrument.log;

import io.micrometer.core.instrument.step.StepRegistryConfig;

public interface LogConfig extends StepRegistryConfig {
    @Override
    default String prefix() {
        return "logging";
    }
}
