package io.micrometer.elastic;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.lang.Nullable;
import io.micrometer.core.tck.MeterRegistryCompatibilityKit;

import java.time.Duration;

public class ElasticMeterRegistryCompatibilityTest extends MeterRegistryCompatibilityKit {
    @Override
    public MeterRegistry registry() {
        return new ElasticMeterRegistry(new ElasticConfig() {
            @Override
            public boolean enabled() {
                return false;
            }

            @Override
            @Nullable
            public String get(String key) {
                return null;
            }
        }, new MockClock());
    }

    @Override
    public Duration step() {
        return ElasticConfig.DEFAULT.step();
    }
}
