/*
 * Copyright 2026 VMware, Inc.
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

import io.opentelemetry.sdk.common.export.HttpResponse;
import io.opentelemetry.sdk.common.export.MessageWriter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OtlpMetricsSenderHttpSenderTests {

    @Test
    void sendDelegatesToOtlpMetricsSender() throws Exception {
        OtlpMetricsSender otlpMetricsSender = mock(OtlpMetricsSender.class);
        String url = "http://localhost:4318/v1/metrics";
        Map<String, String> headers = Collections.singletonMap("key", "value");
        CompressionMode compressionMode = CompressionMode.GZIP;

        OtlpMetricsSenderHttpSender sender = new OtlpMetricsSenderHttpSender(otlpMetricsSender, () -> url,
                () -> headers, () -> compressionMode);

        byte[] payload = "test-payload".getBytes(StandardCharsets.UTF_8);
        MessageWriter writer = new MessageWriter() {
            @Override
            public void writeMessage(OutputStream outputStream) throws java.io.IOException {
                outputStream.write(payload);
            }

            @Override
            public int getContentLength() {
                return payload.length;
            }
        };

        AtomicReference<HttpResponse> responseRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        sender.send(writer, responseRef::set, errorRef::set);

        assertThat(errorRef.get()).isNull();
        assertThat(responseRef.get()).isNotNull();
        assertThat(responseRef.get().getStatusCode()).isEqualTo(200);

        ArgumentCaptor<OtlpMetricsSender.Request> requestCaptor = ArgumentCaptor
            .forClass(OtlpMetricsSender.Request.class);
        verify(otlpMetricsSender).send(requestCaptor.capture());

        OtlpMetricsSender.Request request = requestCaptor.getValue();
        assertThat(request.getAddress()).isEqualTo(url);
        assertThat(request.getHeaders()).isEqualTo(headers);
        assertThat(request.getCompressionMode()).isEqualTo(compressionMode);
        assertThat(request.getMetricsData()).isEqualTo(payload);
    }

    @Test
    void sendPropagatesErrorWhenOtlpMetricsSenderFails() throws Exception {
        OtlpMetricsSender otlpMetricsSender = mock(OtlpMetricsSender.class);
        RuntimeException expectedException = new RuntimeException("send failed");
        doThrow(expectedException).when(otlpMetricsSender).send(any());

        OtlpMetricsSenderHttpSender sender = new OtlpMetricsSenderHttpSender(otlpMetricsSender,
                () -> "http://localhost:4318/v1/metrics", Collections::emptyMap, () -> CompressionMode.NONE);

        MessageWriter writer = new MessageWriter() {
            @Override
            public void writeMessage(OutputStream outputStream) {
            }

            @Override
            public int getContentLength() {
                return 0;
            }
        };

        AtomicReference<HttpResponse> responseRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        sender.send(writer, responseRef::set, errorRef::set);

        assertThat(responseRef.get()).isNull();
        assertThat(errorRef.get()).isSameAs(expectedException);
    }

}
