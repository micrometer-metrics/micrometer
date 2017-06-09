package org.springframework.metrics.export.datadog;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Registry;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.metrics.instrument.spectator.SpectatorMeterRegistry;

public class DatadogMetricsConfiguration {
    @Bean
    SpectatorMeterRegistry meterRegistry(Registry registry) {
        return new SpectatorMeterRegistry(registry);
    }

    @Bean
    DatadogConfig datadogConfig(Environment environment) {
        return environment::getProperty;
    }

    @Bean
    DatadogRegistry atlasRegistry(DatadogConfig atlasConfig) {
        DatadogRegistry registry = new DatadogRegistry(Clock.SYSTEM, atlasConfig);
        registry.start();
        return registry;
    }
}
