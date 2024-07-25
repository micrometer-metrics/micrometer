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

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jersey.server.DefaultJerseyTagsProvider;
import io.micrometer.core.instrument.binder.jersey.server.MetricsApplicationEventListener;
import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.ws.rs.core.Application;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
class Jersey3Test extends JerseyTest {

    static final String TIMER_METRIC_NAME = "http.server.requests";

    MeterRegistry registry;

    @Override
    protected Application configure() {
        registry = new SimpleMeterRegistry();
        MetricsApplicationEventListener metricsListener = new MetricsApplicationEventListener(registry,
                new DefaultJerseyTagsProvider(), TIMER_METRIC_NAME, true);
        return new ResourceConfig(HelloWorldResource.class).register(metricsListener);
    }

    @Test
    void helloResourceIsTimed() throws InterruptedException {
        String response = target("hello/Jersey").request().get(String.class);
        assertThat(response).isEqualTo("Hello, Jersey");
        // Jersey metrics are recorded asynchronously to the request completing
        Thread.sleep(10);
        Timer timer = registry.get(TIMER_METRIC_NAME)
            .tags("method", "GET", "uri", "/hello/{name}", "status", "200", "exception", "None", "outcome", "SUCCESS")
            .timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isPositive();
    }

    @Test
    void loggingMetricsAreCounted() {
        try (LogbackMetrics logbackMetrics = new LogbackMetrics()) {
            logbackMetrics.bindTo(registry);
            InternalLogger logger = InternalLoggerFactory.getInstance(Jersey3Test.class);
            logger.info("test");
            assertThat(registry.get("logback.events").tags("level", "info").counter().count()).isPositive();
        }
    }

}
