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
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.export.HttpResponse;
import io.opentelemetry.sdk.common.export.MessageWriter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

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
    void sendWithOtelHttpSenderDelegation(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
        String path = "/v1/metrics";
        server.stubFor(post(urlEqualTo(path)).willReturn(ok()));

        AtomicBoolean otelSenderCalled = new AtomicBoolean(false);
        io.opentelemetry.sdk.common.export.HttpSender otelSender = new io.opentelemetry.sdk.common.export.HttpSender() {
            @Override
            public void send(MessageWriter messageWriter, Consumer<HttpResponse> onResponse,
                    Consumer<Throwable> onError) {
                otelSenderCalled.set(true);
                try {
                    HttpSender micrometerSender = new HttpUrlConnectionSender();
                    OtlpMicrometerHttpSender micrometerAdapter = new OtlpMicrometerHttpSender(micrometerSender);
                    micrometerAdapter.send(messageWriter, onResponse, onError);
                }
                catch (Throwable t) {
                    onError.accept(t);
                }
            }

            @Override
            public CompletableResultCode shutdown() {
                return CompletableResultCode.ofSuccess();
            }
        };

        OtlpHttpMetricsSender sender = new OtlpHttpMetricsSender(otelSender);
        OtlpMetricsSender.Request request = OtlpMetricsSender.Request.builder("hello".getBytes(StandardCharsets.UTF_8))
            .address(server.url(path))
            .build();

        sender.send(request);

        assertThat(otelSenderCalled).isTrue();
        server.verify(postRequestedFor(urlEqualTo(path)));
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
            .contains("test.counter")
            .contains("1.0")));
    }

}
