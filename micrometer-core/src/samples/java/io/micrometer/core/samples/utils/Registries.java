package io.micrometer.core.samples.utils;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.atlas.AtlasConfig;
import com.netflix.spectator.atlas.AtlasRegistry;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.datadog.DatadogConfig;
import io.micrometer.core.instrument.datadog.DatadogRegistry;
import io.micrometer.core.instrument.prometheus.PrometheusMeterRegistry;
import io.micrometer.core.instrument.spectator.SpectatorMeterRegistry;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Properties;

public class Registries {
    public static PrometheusMeterRegistry prometheus() {
        PrometheusMeterRegistry prometheusRegistry = new PrometheusMeterRegistry();

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
            server.createContext("/prometheus", httpExchange -> {
                String response = prometheusRegistry.scrape();
                httpExchange.sendResponseHeaders(200, response.length());
                OutputStream os = httpExchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            });

            new Thread(server::start).run();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

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
