package io.github.micrometer.appdynamics;

import io.micrometer.core.instrument.step.StepRegistryConfig;

import java.util.concurrent.TimeUnit;

/**
 * @author Ricardo Veloso
 */
public interface AppDynamicsConfig extends StepRegistryConfig {

    AppDynamicsConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "Custom Metrics";
    }

    default TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

}
