package org.springframework.metrics.samples;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.atlas.AtlasConfig;
import com.netflix.spectator.atlas.AtlasRegistry;
import org.springframework.metrics.export.atlas.AtlasUtils;
import org.springframework.metrics.instrument.spectator.SpectatorMeterRegistry;

/**
 * Demonstrates how to push metrics to Atlas explicitly. This is useful
 * for short-lived batch applications.
 *
 * @author Jon Schneider
 */
public class AtlasPushSample {
    public static void main(String[] args) {
        AtlasConfig config = AtlasUtils.pushConfig("http://localhost:7101/api/v1/publish");
        SpectatorMeterRegistry registry = new SpectatorMeterRegistry(new AtlasRegistry(Clock.SYSTEM, config));
        registry.counter("push_counter").increment();

        // push metric and block until completion
        AtlasUtils.atlasPublish(registry, config);
    }
}

