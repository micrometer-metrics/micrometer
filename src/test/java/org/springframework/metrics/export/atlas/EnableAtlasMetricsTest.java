/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.metrics.export.atlas;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.metrics.LocalServer;
import org.springframework.metrics.instrument.Counter;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.TagFormatter;
import org.springframework.metrics.instrument.spectator.SpectatorMeterRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "atlas.step=PT1S")
class EnableAtlasMetricsTest {

    @Autowired
    ApplicationContext context;

    @Test
    void tagFormatting() {
        assertThat(context.getBean(TagFormatter.class))
                .isInstanceOf(AtlasTagFormatter.class);
    }

    @Test
    void meterRegistry() {
        assertThat(context.getBean(MeterRegistry.class))
                .isInstanceOf(SpectatorMeterRegistry.class);
    }

    @Test
    void metricsArePostedToAtlas() throws InterruptedException, LifecycleException {
        CountDownLatch metricsBatchesPosted = new CountDownLatch(1);

        Tomcat mockAtlas = LocalServer.tomcatServer(7101,
                route(POST("/api/v1/publish"), (request) -> {
                    metricsBatchesPosted.countDown();
                    return ServerResponse.ok()
                            .header("Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()))
                            .build();
                })
        );

        try {
            metricsBatchesPosted.await(5000, TimeUnit.SECONDS);
        } finally {
            mockAtlas.stop();
        }
    }

    @SpringBootApplication
    @EnableAtlasMetrics
    @EnableScheduling
    @Import(ScheduledCount.class)
    static class PrometheusApp {
    }

    static class ScheduledCount {
        private final Counter count;

        ScheduledCount(MeterRegistry registry) {
            this.count = registry.counter("counter");
        }

        @Scheduled(fixedRate = 100)
        void increment() {
            count.increment();
        }
    }
}
