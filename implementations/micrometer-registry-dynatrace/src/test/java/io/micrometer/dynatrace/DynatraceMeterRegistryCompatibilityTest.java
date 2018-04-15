package io.micrometer.dynatrace;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.tck.MeterRegistryCompatibilityKit;

import java.time.Duration;

public class DynatraceMeterRegistryCompatibilityTest extends MeterRegistryCompatibilityKit {
    @Override
    public MeterRegistry registry() {
        return new DynatraceMeterRegistry(new DynatraceConfig() {
            @Override
            public boolean enabled() {
                return false;
            }

            @Override
            public String apiToken() {
                return "DOESNOTMATTER";
            }

            @Override
            public String uri() {
                return "http://doesnotmatter.com";
            }

            @Override
            public String deviceId() {
                return "DOESNOTMATTER";
            }

            @Override
            public String get(String key) {
                return null;
            }
        }, new MockClock());
    }

    @Override
    public Duration step() {
        return DynatraceConfig.DEFAULT.step();
    }
}
