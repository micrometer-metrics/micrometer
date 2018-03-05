package io.micrometer.azure;

import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.step.StepRegistryConfig;

public interface AzureConfig extends StepRegistryConfig {
    AzureConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "azure";
    }

    default String apiKey() {
        String v = get(prefix() + ".apiKey");
        if (v == null)
            throw new MissingRequiredConfigurationException("apiKey must be set to report metrics to Application Insights");
        return v;
    }
}
