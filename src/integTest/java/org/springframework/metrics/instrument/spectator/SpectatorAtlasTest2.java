package org.springframework.metrics.instrument.spectator;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.atlas.AtlasRegistry;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

/**
 * Trying to figure out how to get the experimental Spectator Atlas module publishing metrics
 */
class SpectatorAtlasTest2 {
    ManualClock clock = new ManualClock();

    AtlasRegistry registry = new AtlasRegistry(Clock.SYSTEM, new HashMap<String, String>() {{
        put("atlas.step", "PT10S");
        put("atlas.batchSize", "3");
    }}::get);

    @Test
    @Disabled
    void publishMetrics() {
        registry.start();
        registry.counter("myCounter");
    }
}
