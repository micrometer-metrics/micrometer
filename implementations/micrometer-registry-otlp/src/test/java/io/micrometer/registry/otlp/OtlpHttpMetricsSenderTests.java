/*
 * Copyright 2025 VMware, Inc.
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

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.util.Collections;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link OtlpHttpMetricsSender}.
 *
 * @author Johnny Lim
 */
@ExtendWith(WiremockResolver.class)
class OtlpHttpMetricsSenderTests {

    @Test
    void sendWhenResponseIsUnsuccessful(@WiremockResolver.Wiremock WireMockServer server) {
        String path = "/metrics";
        server.stubFor(any(urlEqualTo(path)).willReturn(badRequest()));

        HttpSender httpSender = new HttpUrlConnectionSender();
        OtlpHttpMetricsSender otlpHttpMetricsSender = new OtlpHttpMetricsSender(httpSender);
        OtlpMetricsSender.Request request = OtlpMetricsSender.Request.builder(new byte[0])
            .address(server.url(path))
            .build();
        assertThatException().isThrownBy(() -> otlpHttpMetricsSender.send(request))
            .satisfies((ex) -> assertThat(ex.getClass().getSimpleName())
                .isEqualTo("OtlpHttpMetricsSendUnsuccessfulException"));
    }

    @Test
    void toStringOfRequestShouldBeHumanReadable() throws Exception {
        OtlpConfig config = new OtlpConfig() {
            @Override
            public @NonNull Map<String, String> headers() {
                return Collections.singletonMap("test-key", "test-value");
            }

            @Override
            public @Nullable String get(@NonNull String key) {
                return null;
            }
        };
        OtlpMetricsSender metricsSender = mock(OtlpMetricsSender.class);
        MeterRegistry registry = OtlpMeterRegistry.builder(config).metricsSender(metricsSender).build();
        registry.counter("test.counter").increment();
        registry.close();

        verify(metricsSender).send(assertArg(request -> assertThat(request.toString()).startsWith(
                "OtlpMetricsSender.Request for address: http://localhost:4318/v1/metrics, headers: {test-key=test-value}, compressionMode: NONE, metricsData:")
            .contains("name: \"test.counter\"")
            .contains("as_double: 1.0")
            .contains("aggregation_temporality: AGGREGATION_TEMPORALITY_CUMULATIVE")));
    }

}
