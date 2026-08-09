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

import io.micrometer.core.ipc.http.HttpSender;
import io.opentelemetry.sdk.common.export.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An implementation of {@link OtlpMetricsSender} that delegates sending to OpenTelemetry's
 * {@link io.opentelemetry.sdk.common.export.HttpSender}.
 *
 * @since 1.15.0
 */
public class OtlpHttpMetricsSender implements OtlpMetricsSender {

    private final io.opentelemetry.sdk.common.export.HttpSender otelHttpSender;

    /**
     * Metrics sender using the given Micrometer {@link HttpSender}.
     * @param httpSender client to use to send metrics
     */
    public OtlpHttpMetricsSender(HttpSender httpSender) {
        this(new OtlpMicrometerHttpSender(httpSender));
    }

    /**
     * Metrics sender using OpenTelemetry's {@link io.opentelemetry.sdk.common.export.HttpSender}.
     * @param otelHttpSender OpenTelemetry HTTP sender to use
     */
    public OtlpHttpMetricsSender(io.opentelemetry.sdk.common.export.HttpSender otelHttpSender) {
        this.otelHttpSender = Objects.requireNonNull(otelHttpSender, "otelHttpSender");
    }

    /**
     * Send a batch of OTLP Protobuf format metrics to an OTLP HTTP receiver.
     * @param request metrics request to publish
     * @throws Exception when there is an exception in sending the metrics; the caller
     * should handle this in some way such as logging the exception
     */
    @Override
    public void send(Request request) throws Exception {
        if (request.getAddress() == null) {
            throw new IllegalArgumentException("Address cannot be null");
        }

        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<HttpResponse> responseRef = new AtomicReference<>();

        OtlpMicrometerHttpSender.OtlpMetricsSenderRequestMessageWriter messageWriter = new OtlpMicrometerHttpSender.OtlpMetricsSenderRequestMessageWriter(
                request);

        this.otelHttpSender.send(
            messageWriter,
            responseRef::set,
            errorRef::set
        );

        if (errorRef.get() != null) {
            Throwable cause = errorRef.get();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new Exception(cause);
        }

        HttpResponse response = responseRef.get();
        if (response != null && (response.getStatusCode() < 200 || response.getStatusCode() >= 300)) {
            String body = "";
            byte[] responseBody = response.getResponseBody();
            if (responseBody != null) {
                body = new String(responseBody, StandardCharsets.UTF_8);
            }
            throw new OtlpHttpMetricsSendUnsuccessfulException(String
                .format("Server responded with HTTP status code %d and body %s", response.getStatusCode(), body));
        }
    }

    private static class OtlpHttpMetricsSendUnsuccessfulException extends RuntimeException {

        private OtlpHttpMetricsSendUnsuccessfulException(String message) {
            super(message);
        }

    }

}
