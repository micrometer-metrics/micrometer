package org.springframework.metrics.samples.utils;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.atlas.AtlasConfig;
import com.netflix.spectator.atlas.AtlasRegistry;
import org.springframework.metrics.export.datadog.DatadogConfig;
import org.springframework.metrics.export.datadog.DatadogRegistry;
import org.springframework.metrics.export.prometheus.PrometheusFunctions;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.prometheus.PrometheusMeterRegistry;
import org.springframework.metrics.instrument.spectator.SpectatorMeterRegistry;
import org.springframework.util.SystemPropertyUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

public class Registries {
    public static PrometheusMeterRegistry prometheus() {
        PrometheusMeterRegistry prometheusRegistry = new PrometheusMeterRegistry();
        LocalServer.tomcatServer(8080, route(GET("/prometheus"),
                PrometheusFunctions.scrape(prometheusRegistry)));
        return prometheusRegistry;
    }

    public static SpectatorMeterRegistry atlas() {
        AtlasRegistry spectatorAtlas = new AtlasRegistry(Clock.SYSTEM, new AtlasConfig() {
            @Override
            public Duration step() {
                return Duration.ofSeconds(10);
            }

            @Override
            public String get(String k) {
                return null;
            }
        });
        spectatorAtlas.start();
        return new SpectatorMeterRegistry(spectatorAtlas);
    }

    public static SpectatorMeterRegistry datadog() {
        DatadogRegistry spectatorDatadog = new DatadogRegistry(Clock.SYSTEM, new DatadogConfig() {
            private final Properties props = new Properties();

            {
                try {
                    props.load(Registries.class.getResourceAsStream("/application.properties"));
                } catch (IOException e) {
                    throw new RuntimeException("must have application.properties with datadog.apiKey defined", e);
                }
            }

            @Override
            public String get(String k) {
                return props.getProperty(k);
            }
        });
        spectatorDatadog.start();
        return new SpectatorMeterRegistry(spectatorDatadog);
    }
}
