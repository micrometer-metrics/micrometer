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

import io.opentelemetry.exporter.internal.otlp.metrics.MetricsRequestMarshaler;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.export.HttpResponse;
import io.opentelemetry.sdk.common.export.HttpSender;
import io.opentelemetry.sdk.common.export.MessageWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An OpenTelemetry {@link HttpSender} implementation that delegates sending to a
 * configured Micrometer {@link OtlpMetricsSender}.
 */
class OtlpMetricsSenderHttpSender implements HttpSender {

    private final OtlpMetricsSender otlpMetricsSender;

    private final Supplier<String> urlSupplier;

    private final Supplier<Map<String, String>> headersSupplier;

    private final Supplier<CompressionMode> compressionModeSupplier;

    OtlpMetricsSenderHttpSender(OtlpMetricsSender otlpMetricsSender, Supplier<String> urlSupplier,
            Supplier<Map<String, String>> headersSupplier, Supplier<CompressionMode> compressionModeSupplier) {
        this.otlpMetricsSender = Objects.requireNonNull(otlpMetricsSender, "otlpMetricsSender");
        this.urlSupplier = Objects.requireNonNull(urlSupplier, "urlSupplier");
        this.headersSupplier = Objects.requireNonNull(headersSupplier, "headersSupplier");
        this.compressionModeSupplier = Objects.requireNonNull(compressionModeSupplier, "compressionModeSupplier");
    }

    @Override
    public void send(MessageWriter messageWriter, Consumer<HttpResponse> onResponse, Consumer<Throwable> onError) {
        try {
            int contentLength = messageWriter.getContentLength();
            ByteArrayOutputStream baos = new ByteArrayOutputStream(contentLength > 0 ? contentLength : 1024);
            messageWriter.writeMessage(baos);
            byte[] metricsData = baos.toByteArray();

            OtlpMetricsSender.Request.Builder builder = OtlpMetricsSender.Request.builder(metricsData)
                .address(urlSupplier.get())
                .headers(headersSupplier.get())
                .compressionMode(compressionModeSupplier.get());

            if (messageWriter instanceof MarshalerMessageWriter) {
                MetricsRequestMarshaler marshaler = ((MarshalerMessageWriter) messageWriter).getMarshaler();
                builder.readableMetricsData(() -> {
                    try {
                        ByteArrayOutputStream jsonOs = new ByteArrayOutputStream();
                        marshaler.writeJsonTo(jsonOs);
                        return new String(jsonOs.toByteArray(), StandardCharsets.UTF_8);
                    }
                    catch (Throwable t) {
                        return "<failed to serialize json>";
                    }
                });
            }

            OtlpMetricsSender.Request request = builder.build();
            otlpMetricsSender.send(request);

            onResponse.accept(new HttpResponse() {
                @Override
                public int getStatusCode() {
                    return 200;
                }

                @Override
                public String getStatusMessage() {
                    return "OK";
                }

                @Override
                public byte[] getResponseBody() {
                    return new byte[0];
                }
            });
        }
        catch (Throwable t) {
            onError.accept(t);
        }
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }

    static class MarshalerMessageWriter implements MessageWriter {

        private final MetricsRequestMarshaler marshaler;

        MarshalerMessageWriter(MetricsRequestMarshaler marshaler) {
            this.marshaler = Objects.requireNonNull(marshaler, "marshaler");
        }

        MetricsRequestMarshaler getMarshaler() {
            return marshaler;
        }

        @Override
        public void writeMessage(OutputStream outputStream) throws IOException {
            marshaler.writeBinaryTo(outputStream);
        }

        @Override
        public int getContentLength() {
            return marshaler.getBinarySerializedSize();
        }

    }

}
