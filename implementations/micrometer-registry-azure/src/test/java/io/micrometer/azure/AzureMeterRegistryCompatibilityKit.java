package io.micrometer.azure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.tck.MeterRegistryCompatibilityKit;

import java.time.Duration;

public class AzureMeterRegistryCompatibilityKit extends MeterRegistryCompatibilityKit {
    @Override
    public MeterRegistry registry() {
        return new AzureMeterRegistry(new AzureConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public boolean enabled() {
                return false;
            }
        }, new MockClock());
    }

    @Override
    public Duration step() {
        return AzureConfig.DEFAULT.step();
    }
}
