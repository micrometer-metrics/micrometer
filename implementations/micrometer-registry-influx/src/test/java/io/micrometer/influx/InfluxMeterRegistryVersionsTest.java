/**
 * Copyright 2020 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.influx;

import java.util.concurrent.TimeUnit;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.lang.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.headRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * Unit tests for check compatibility of {@link InfluxMeterRegistry} with InfluxDB v1 and InfluxDB v2.
 *
 * @author Jakub Bednar (19/05/2020 09:00)
 */
@ExtendWith(WiremockResolver.class)
public class InfluxMeterRegistryVersionsTest {

    @Test
    void writeToV1PingWithVersion(@WiremockResolver.Wiremock WireMockServer server) {

        server.stubFor(any(urlEqualTo("/ping"))
                .willReturn(aResponse().withStatus(200).withHeader("X-Influxdb-Version", "1.7.10")));
        server.stubFor(any(urlEqualTo("/write"))
                .willReturn(aResponse().withStatus(204)));

        publishSimpleStat(server);

        server.verify(headRequestedFor(urlEqualTo("/ping")));
        server.verify(postRequestedFor(urlEqualTo("/write?consistency=one&precision=ms&db=mydb"))
                .withRequestBody(equalTo("my_counter,metric_type=counter value=0 1")));
    }

    @Test
    void writeToV2PingWithVersion(@WiremockResolver.Wiremock WireMockServer server) {

        server.stubFor(any(urlEqualTo("/ping"))
                .willReturn(aResponse().withStatus(200).withHeader("x-influxdb-version", "2.0")));
        server.stubFor(any(urlEqualTo("/api/v2/write"))
                .willReturn(aResponse().withStatus(204)));

        publishSimpleStat(server);

        server.verify(headRequestedFor(urlEqualTo("/ping")));
        server.verify(postRequestedFor(urlEqualTo("/api/v2/write?&precision=ms&bucket=mydb"))
                .withRequestBody(equalTo("my_counter,metric_type=counter value=0 1")));
    }

    @Test
    void writeToV2PingWithoutVersion(@WiremockResolver.Wiremock WireMockServer server) {

        server.stubFor(any(urlEqualTo("/ping"))
                .willReturn(aResponse().withStatus(200)));
        server.stubFor(any(urlEqualTo("/api/v2/write"))
                .willReturn(aResponse().withStatus(204)));

        publishSimpleStat(server);

        server.verify(headRequestedFor(urlEqualTo("/ping")));
        server.verify(postRequestedFor(urlEqualTo("/api/v2/write?&precision=ms&bucket=mydb"))
                .withRequestBody(equalTo("my_counter,metric_type=counter value=0 1")));
    }

    @Test
    void writeToV2PingError(@WiremockResolver.Wiremock WireMockServer server) {

        server.stubFor(any(urlEqualTo("/ping"))
                .willReturn(aResponse().withStatus(500)));
        server.stubFor(any(urlEqualTo("/api/v2/write"))
                .willReturn(aResponse().withStatus(204)));

        publishSimpleStat(server);

        server.verify(headRequestedFor(urlEqualTo("/ping")));
        server.verify(postRequestedFor(urlEqualTo("/api/v2/write?&precision=ms&bucket=mydb"))
                .withRequestBody(equalTo("my_counter,metric_type=counter value=0 1")));
    }

    private void publishSimpleStat(@WiremockResolver.Wiremock final WireMockServer server) {
        InfluxMeterRegistry registry = new InfluxMeterRegistry(new InfluxConfig() {
            @Override
            public String uri() {
                return server.baseUrl();
            }

            @Override
            @Nullable
            public String get(String key) {
                return null;
            }
        }, new MockClock());

        Counter.builder("my.counter")
                .baseUnit(TimeUnit.MICROSECONDS.toString().toLowerCase())
                .description("metric description")
                .register(registry)
                .increment(Math.PI);
        registry.publish();
    }
}
