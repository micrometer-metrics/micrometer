/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.registry.otlp;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.Clock;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@WireMockTest
class OtlpMeterRegistryExportTest {

    @Test
    void checkHttpRequest(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(post("/v1/metrics").willReturn(ok()));
        OtlpMeterRegistry registry = new OtlpMeterRegistry(new OtlpConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String url() {
                return wmRuntimeInfo.getHttpBaseUrl() + "/v1/metrics";
            }

            @Override
            public Map<String, String> headers() {
                return Collections.singletonMap("API-Key", "super-secret-token");
            }
        }, Clock.SYSTEM);
        registry.counter("publish").increment();
        registry.publish();

        verify(postRequestedFor(urlEqualTo("/v1/metrics")).withHeader("API-Key", equalTo("super-secret-token")));
    }

}
