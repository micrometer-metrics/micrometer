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
package io.micrometer.spring.export.atlas;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.spectator.SpectatorMeterRegistry;
import org.apache.catalina.LifecycleException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "atlas.step=PT1S")
public class EnableAtlasMetricsTest {

    @Autowired
    ApplicationContext context;

    @Test
    public void meterRegistry() {
        assertThat(context.getBean(MeterRegistry.class))
                .isInstanceOf(SpectatorMeterRegistry.class);
    }

    @Test
    public void metricsArePostedToAtlas() throws InterruptedException, LifecycleException {
        CountDownLatch metricsBatchesPosted = new CountDownLatch(1);

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(7101), 0);
            server.createContext("/api/v1/publish", httpExchange -> {
                httpExchange.getResponseHeaders()
                        .add("Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()));
                httpExchange.sendResponseHeaders(200, 0);
                metricsBatchesPosted.countDown();
            });

            new Thread(server::start).run();

            try {
                metricsBatchesPosted.await(5000, TimeUnit.SECONDS);
            } finally {
                server.stop(0);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SpringBootApplication(scanBasePackages = "isolated")
    @EnableAtlasMetrics
    @EnableScheduling
    @Import(ScheduledCount.class)
    static class AtlasApp {
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
