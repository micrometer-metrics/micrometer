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

/**
 * An implementation of {@link OtlpMetricsSender} that uses an {@link HttpSender}.
 *
 * @since 1.15.0
 */
public class OtlpHttpMetricsSender implements OtlpMetricsSender {

    private final HttpSender httpSender;

    private final String userAgentHeader;

    /**
     * Metrics sender using the given {@link HttpSender}.
     * @param httpSender client to use to send metrics
     */
    public OtlpHttpMetricsSender(HttpSender httpSender) {
        this.httpSender = httpSender;
        this.userAgentHeader = getUserAgentHeader();
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

        HttpSender.Request.Builder httpRequest = this.httpSender.post(request.getAddress())
            .withHeader("User-Agent", userAgentHeader)
            .withContent("application/x-protobuf", request.getMetricsData());
        request.getHeaders().forEach(httpRequest::withHeader);
        HttpSender.Response response;
        try {
            response = httpRequest.send();
        }
        catch (Throwable e) {
            throw new Exception(e);
        }
        if (!response.isSuccessful()) {
            throw new OtlpHttpMetricsSendUnsuccessfulException(String
                .format("Server responded with HTTP status code %d and body %s", response.code(), response.body()));
        }
    }

    private String getUserAgentHeader() {
        String userAgent = "Micrometer-OTLP-Exporter-Java";
        String version = getClass().getPackage().getImplementationVersion();
        if (version != null) {
            userAgent += "/" + version;
        }
        return userAgent;
    }

    private static class OtlpHttpMetricsSendUnsuccessfulException extends RuntimeException {

        private OtlpHttpMetricsSendUnsuccessfulException(String message) {
            super(message);
        }

    }

}
