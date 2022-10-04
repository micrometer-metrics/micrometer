package io.github.micrometer.appdynamics;

import io.micrometer.core.instrument.Meter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PathNamingConventionTest {

    @Test
    void shouldConvertPathName() {
        AppDynamicsConfig config = AppDynamicsConfig.DEFAULT;

        PathNamingConvention victim = new PathNamingConvention(config);
        String name = victim.name("counter", Meter.Type.COUNTER);

        assertEquals(config.prefix() + "|counter", name);
    }

}
