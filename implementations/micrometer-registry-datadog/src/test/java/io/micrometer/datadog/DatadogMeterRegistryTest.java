/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.datadog;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Clock;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.ipc.netty.http.server.HttpServer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Fails on CircleCI?")
class DatadogMeterRegistryTest {

    @Issue("#463")
    @Test
    void encodeMetricName() throws InterruptedException {
        DatadogMeterRegistry registry = new DatadogMeterRegistry(new DatadogConfig() {
            @Override
            public String uri() {
                return "http://localhost:3036";
            }

            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String apiKey() {
                return "fake";
            }

            @Override
            public String applicationKey() {
                return "fake";
            }

            @Override
            public boolean enabled() {
                return false;
            }
        }, Clock.SYSTEM);

        CountDownLatch metadataRequests = new CountDownLatch(1);
        AtomicReference<String> metadataMetricName = new AtomicReference<>();

        Pattern p = Pattern.compile("/api/v1/metrics/([^\\?]+)\\?.*");

        Disposable server = HttpServer.create(3036)
                .newHandler((req, resp) -> {
                    Matcher matcher = p.matcher(req.uri());
                    if (matcher.matches()) {
                        metadataMetricName.set(matcher.group(1));
                        metadataRequests.countDown();
                    }
                    return req.receive().then(resp.status(200).send());
                })
                .subscribe();

        try {
            registry.counter("my.counter#abc").increment();
            registry.publish();
            metadataRequests.await(10, TimeUnit.SECONDS);
            assertThat(metadataMetricName.get()).isEqualTo("my.counter%23abc");
        } finally {
            server.dispose();
        }
    }
}