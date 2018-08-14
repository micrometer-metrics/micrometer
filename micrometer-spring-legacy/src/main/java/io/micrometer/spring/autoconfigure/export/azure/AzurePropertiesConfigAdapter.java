package io.micrometer.spring.autoconfigure.export.azure;

import io.micrometer.azure.AzureConfig;
import io.micrometer.spring.autoconfigure.export.StepRegistryPropertiesConfigAdapter;

public class AzurePropertiesConfigAdapter extends StepRegistryPropertiesConfigAdapter<AzureProperties>
implements AzureConfig {

    public AzurePropertiesConfigAdapter(
        AzureProperties properties) {
        super(properties);
    }

    @Override
    public String instrumentationKey() {
        return get(AzureProperties::getInstrumentationKey, AzureConfig.super::instrumentationKey);
    }
}
