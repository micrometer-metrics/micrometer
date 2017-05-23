package org.springframework.metrics.export.atlas;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.metrics.instrument.spectator.SpectatorMeterRegistry;

@Configuration
public class AtlasMetricsConfiguration {
    @Bean
    AtlasTagFormatter tagFormatter() {
        return new AtlasTagFormatter();
    }

    @Bean
    SpectatorMeterRegistry meterRegistry() {
        return new SpectatorMeterRegistry();
    }
}
