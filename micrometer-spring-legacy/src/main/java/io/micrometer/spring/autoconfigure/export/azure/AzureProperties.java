package io.micrometer.spring.autoconfigure.export.azure;

import io.micrometer.spring.autoconfigure.export.StepRegistryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "management.metrics.azure.application-insights")
public class AzureProperties extends StepRegistryProperties {

    private String instrumentationKey;

    public String getInstrumentationKey() {
        return instrumentationKey;
    }

    public void setInstrumentationKey(String instrumentationKey) {
        this.instrumentationKey = instrumentationKey;
    }
}
