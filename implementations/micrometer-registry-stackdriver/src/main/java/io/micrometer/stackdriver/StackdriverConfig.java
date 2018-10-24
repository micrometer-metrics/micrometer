package io.micrometer.stackdriver;

import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.step.StepRegistryConfig;

public interface StackdriverConfig extends StepRegistryConfig {
    @Override
    default String prefix() {
        return "stackdriver";
    }

    default String projectId() {
        String v = get(prefix() + ".projectId");
        if (v == null)
            throw new MissingRequiredConfigurationException("projectId must be set to report metrics to Stackdriver");
        return v;
    }

    default String[] resourceTags() {
        String v = get(prefix() + ".resourceTags");
        if(v == null)
            return new String[0];
        return v.split(",");
    }


    default String pathPrefix() {
        String v = get(".pathPrefix");
        return v == null ? "micrometer.io" : v;
    }
}
