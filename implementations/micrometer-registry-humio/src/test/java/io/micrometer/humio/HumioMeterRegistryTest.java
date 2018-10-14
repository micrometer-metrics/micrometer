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
package io.micrometer.humio;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.instrument.MockClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@ExtendWith(WiremockResolver.class)
class HumioMeterRegistryTest {
    private MockClock clock = new MockClock();

    @Test
    void writeTimer(@WiremockResolver.Wiremock WireMockServer server) {
        HumioMeterRegistry registry = humioRegistry(server);
        registry.timer("my.timer", "status", "success");

        server.stubFor(any(anyUrl()));
        registry.publish();
        server.verify(postRequestedFor(urlMatching("/api/v1/dataspaces/repo/ingest"))
                .withRequestBody(equalTo("[{\"events\": [{\"timestamp\":\"1970-01-01T00:00:00.001Z\",\"attributes\":{\"name\":\"my_timer\",\"count\":0,\"sum\":0,\"avg\":0,\"max\":0,\"status\":\"success\"}}]}]")));
    }

    @Test
    void datasourceTags(@WiremockResolver.Wiremock WireMockServer server) {
        HumioMeterRegistry registry = humioRegistry(server, "name", "micrometer");
        registry.counter("my.counter").increment();

        server.stubFor(any(anyUrl()));
        registry.publish();
        server.verify(postRequestedFor(urlMatching("/api/v1/dataspaces/repo/ingest"))
                .withRequestBody(containing("\"tags\":{\"name\": \"micrometer\"}")));
    }

    private HumioMeterRegistry humioRegistry(WireMockServer server, String... tags) {
        return new HumioMeterRegistry(new HumioConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String uri() {
                return server.baseUrl();
            }

            @Override
            public String repository() {
                return "repo";
            }

            @Override
            public Map<String, String> tags() {
                Map<String, String> tagMap = new HashMap<>();
                for (int i = 0; i < tags.length; i += 2) {
                    tagMap.put(tags[i], tags[i + 1]);
                }
                return tagMap;
            }
        }, clock);
    }
}