/*
 * Copyright 2021 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.samples.jersey3;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jersey.server.DefaultJerseyTagsProvider;
import io.micrometer.core.instrument.binder.jersey.server.MetricsApplicationEventListener;
import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import io.micrometer.core.instrument.logging.LoggingMeterRegistry;
import io.micrometer.core.instrument.logging.LoggingRegistryConfig;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.ext.RuntimeDelegate;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;

@SuppressWarnings("deprecation")
public class Jersey3Main {

    public static void main(String[] args) throws IOException {
        MeterRegistry registry = new LoggingMeterRegistry(new LoggingRegistryConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public Duration step() {
                return Duration.ofSeconds(10);
            }
        }, Clock.SYSTEM);
        LogbackMetrics logbackMetrics = new LogbackMetrics();
        Runtime.getRuntime().addShutdownHook(new Thread(logbackMetrics::close));
        logbackMetrics.bindTo(registry);
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        Application application = new ResourceConfig(HelloWorldResource.class)
            .register(new MetricsApplicationEventListener(registry, new DefaultJerseyTagsProvider(),
                    "http.server.requests", true));
        server.createContext("/", RuntimeDelegate.getInstance().createEndpoint(application, HttpHandler.class));

        server.start();
    }

}
