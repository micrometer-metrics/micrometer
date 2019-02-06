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

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@ExtendWith(WiremockResolver.class)
class DatadogMeterRegistryTest {

    @Issue("#463")
    @Test
    void encodeMetricName(@WiremockResolver.Wiremock WireMockServer server) {
        DatadogMeterRegistry registry = new DatadogMeterRegistry(new DatadogConfig() {
            @Override
            public String uri() {
                return server.baseUrl();
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

        server.stubFor(any(anyUrl()));

        Counter.builder("my.counter#abc")
            .baseUnit(TimeUnit.MICROSECONDS.toString().toLowerCase())
            .register(registry)
            .increment(Math.PI);
        registry.publish();

        server.verify(putRequestedFor(
            urlMatching("/api/v1/metrics/my.counter%23abc?.+"))
            .withRequestBody(equalToJson("{\"type\":\"count\",\"unit\":\"microsecond\"}")
            ));

        registry.close();
    }
}