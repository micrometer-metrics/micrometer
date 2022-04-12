package io.micrometer.registry.otlp;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.tck.MeterRegistryCompatibilityKit;

import java.time.Duration;

class OtlpMeterRegistryCompatibilityTest extends MeterRegistryCompatibilityKit  {

    @Override
    public MeterRegistry registry() {
        return new OtlpMeterRegistry(new OtlpConfig() {
            @Override
            public boolean enabled() {
                return false;
            }

            @Override
            public String get(String key) {
                return null;
            }
        }, new MockClock());
    }

    @Override
    public Duration step() {
        return OtlpConfig.DEFAULT.step();
    }
}
