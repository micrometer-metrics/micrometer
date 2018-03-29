package io.micrometer.dynatrace;

import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.lang.Nullable;

/**
 * Configuration for {@link DynatraceMeterRegistry}
 *
 * @author Oriol Barcelona
 */
public interface DynatraceConfig extends StepRegistryConfig {

    DynatraceConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "dynatrace";
    }

    default String apiToken() {
        String v = get(prefix() + ".apiToken");
        if (v == null)
            throw new MissingRequiredConfigurationException("apiToken must be set to report metrics to Dynatrace");
        return v;
    }

    default String uri() {
        String v = get(prefix() + ".uri");
        if (v == null)
            throw new MissingRequiredConfigurationException("uri must be set to report metrics to Dynatrace");
        return v;
    }

    default String deviceId() {
        String v = get(prefix() + ".deviceId");
        if (v == null)
            throw new MissingRequiredConfigurationException("deviceId must be set to report metrics to Dynatrace");
        return v;
    }
}
