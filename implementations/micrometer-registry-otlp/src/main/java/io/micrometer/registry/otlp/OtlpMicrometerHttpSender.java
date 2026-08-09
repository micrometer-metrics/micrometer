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

import io.micrometer.core.ipc.http.HttpSender;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.export.HttpResponse;
import io.opentelemetry.sdk.common.export.MessageWriter;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An OpenTelemetry {@link io.opentelemetry.sdk.common.export.HttpSender} that delegates
 * sending OTLP protobuf requests to Micrometer's {@link HttpSender}.
 *
 * @since 1.15.0
 */
public class OtlpMicrometerHttpSender implements io.opentelemetry.sdk.common.export.HttpSender {

    private final HttpSender micrometerHttpSender;

    private final @Nullable Supplier<String> urlSupplier;

    private final @Nullable Supplier<Map<String, String>> headersSupplier;

    private final @Nullable Supplier<CompressionMode> compressionModeSupplier;

    private final String userAgentHeader;

    /**
     * Create an adapter delegating to Micrometer's {@link HttpSender}.
     * @param micrometerHttpSender Micrometer HTTP sender to delegate to
     */
    public OtlpMicrometerHttpSender(HttpSender micrometerHttpSender) {
        this(micrometerHttpSender, null, null, null);
    }

    /**
     * Create an adapter delegating to Micrometer's {@link HttpSender} with endpoint suppliers.
     * @param micrometerHttpSender Micrometer HTTP sender
     * @param urlSupplier supplier for the target URL
     * @param headersSupplier supplier for request headers
     * @param compressionModeSupplier supplier for compression mode
     */
    public OtlpMicrometerHttpSender(HttpSender micrometerHttpSender, @Nullable Supplier<String> urlSupplier,
            @Nullable Supplier<Map<String, String>> headersSupplier,
            @Nullable Supplier<CompressionMode> compressionModeSupplier) {
        this.micrometerHttpSender = Objects.requireNonNull(micrometerHttpSender, "micrometerHttpSender");
        this.urlSupplier = urlSupplier;
        this.headersSupplier = headersSupplier;
        this.compressionModeSupplier = compressionModeSupplier;
        this.userAgentHeader = getUserAgentHeader();
    }

    @Override
    public void send(MessageWriter messageWriter, Consumer<HttpResponse> onResponse, Consumer<Throwable> onError) {
        try {
            String address = null;
            Map<String, String> headers = null;
            CompressionMode compressionMode = CompressionMode.NONE;
            byte[] body;

            if (messageWriter instanceof OtlpMetricsSenderRequestMessageWriter) {
                OtlpMetricsSenderRequestMessageWriter requestWriter = (OtlpMetricsSenderRequestMessageWriter) messageWriter;
                OtlpMetricsSender.Request request = requestWriter.getRequest();
                address = request.getAddress();
                headers = request.getHeaders();
                compressionMode = request.getCompressionMode();
                body = request.getMetricsData();
            }
            else {
                if (urlSupplier != null) {
                    address = urlSupplier.get();
                }
                if (headersSupplier != null) {
                    headers = headersSupplier.get();
                }
                if (compressionModeSupplier != null) {
                    CompressionMode mode = compressionModeSupplier.get();
                    if (mode != null) {
                        compressionMode = mode;
                    }
                }
                int contentLength = messageWriter.getContentLength();
                ByteArrayOutputStream baos = new ByteArrayOutputStream(contentLength > 0 ? contentLength : 1024);
                messageWriter.writeMessage(baos);
                body = baos.toByteArray();
            }

            if (address == null) {
                throw new IllegalArgumentException("Address cannot be null");
            }

            HttpSender.Request.Builder httpRequest = this.micrometerHttpSender.post(address)
                .withHeader("User-Agent", userAgentHeader)
                .withContent("application/x-protobuf", body);

            if (headers != null) {
                headers.forEach(httpRequest::withHeader);
            }

            if (compressionMode == CompressionMode.GZIP) {
                httpRequest.compress();
            }

            HttpSender.Response response = httpRequest.send();
            onResponse.accept(new OtelHttpResponseAdapter(response));
        }
        catch (Throwable t) {
            onError.accept(t);
        }
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }

    private String getUserAgentHeader() {
        String userAgent = "Micrometer-OTLP-Exporter-Java";
        String version = getClass().getPackage().getImplementationVersion();
        if (version != null) {
            userAgent += "/" + version;
        }
        return userAgent;
    }

    private static class OtelHttpResponseAdapter implements HttpResponse {

        private final HttpSender.Response response;

        private OtelHttpResponseAdapter(HttpSender.Response response) {
            this.response = response;
        }

        @Override
        public int getStatusCode() {
            return response.code();
        }

        @Override
        public String getStatusMessage() {
            return "HTTP " + response.code();
        }

        @Override
        public byte[] getResponseBody() {
            return response.body().getBytes(StandardCharsets.UTF_8);
        }

    }

    /**
     * MessageWriter adapter for {@link OtlpMetricsSender.Request}.
     */
    public static class OtlpMetricsSenderRequestMessageWriter implements MessageWriter {

        private final OtlpMetricsSender.Request request;

        public OtlpMetricsSenderRequestMessageWriter(OtlpMetricsSender.Request request) {
            this.request = Objects.requireNonNull(request, "request");
        }

        public OtlpMetricsSender.Request getRequest() {
            return request;
        }

        @Override
        public int getContentLength() {
            return request.getMetricsData().length;
        }

        @Override
        public void writeMessage(OutputStream outputStream) throws IOException {
            outputStream.write(request.getMetricsData());
        }

    }

}
