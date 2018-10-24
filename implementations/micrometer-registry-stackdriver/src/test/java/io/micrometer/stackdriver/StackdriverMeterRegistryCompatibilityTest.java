package io.micrometer.stackdriver;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.lang.Nullable;
import io.micrometer.core.tck.MeterRegistryCompatibilityKit;

import java.time.Duration;

public class StackdriverMeterRegistryCompatibilityTest extends MeterRegistryCompatibilityKit {

    private final StackdriverConfig config = new StackdriverConfig() {
        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public String projectId() {
            return "doesnotmatter";
        }

        @Override
        @Nullable
        public String get(String key) {
            return null;
        }
    };

    @Override
    public MeterRegistry registry() {
        return new StackdriverMeterRegistry(config, new MockClock());
    }

    @Override
    public Duration step() {
        return config.step();
    }
}

