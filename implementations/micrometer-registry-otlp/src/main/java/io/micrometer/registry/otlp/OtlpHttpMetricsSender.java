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

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.ipc.http.HttpSender;

import java.util.Map;

/**
 * An implementation of {@link OtlpMetricsSender} that uses an {@link HttpSender}.
 *
 * @since 1.15.0
 */
public class OtlpHttpMetricsSender implements OtlpMetricsSender {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(OtlpHttpMetricsSender.class);

    private final HttpSender httpSender;

    private final String userAgentHeader;

    public OtlpHttpMetricsSender(HttpSender httpSender) {
        this.httpSender = httpSender;
        this.userAgentHeader = getUserAgentHeader();
    }

    /**
     * Send a batch of OTLP Protobuf format metrics to an OTLP HTTP receiver.
     * @param address address of the OTLP HTTP receiver to which metrics will be sent
     * @param metricsData OTLP protobuf encoded batch of metrics
     * @param headers metadata to send as headers with the metrics data
     * @throws Throwable when there is an exception in sending the metrics; the caller
     * should handle this in some way such as logging the exception
     */
    @Override
    public void send(String address, byte[] metricsData, Map<String, String> headers) throws Throwable {
        HttpSender.Request.Builder httpRequest = this.httpSender.post(address)
            .withHeader("User-Agent", userAgentHeader)
            .withContent("application/x-protobuf", metricsData);
        headers.forEach(httpRequest::withHeader);
        HttpSender.Response response = httpRequest.send();
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

        public OtlpHttpMetricsSendUnsuccessfulException(String message) {
            super(message);
        }

    }

}
