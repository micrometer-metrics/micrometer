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

    private final OtlpConfig config;

    private final String userAgentHeader;

    public OtlpHttpMetricsSender(HttpSender httpSender, OtlpConfig config) {
        this.httpSender = httpSender;
        this.config = config;
        this.userAgentHeader = getUserAgentHeader();
    }

    @Override
    public void send(byte[] metricsData, Map<String, String> headers) {
        HttpSender.Request.Builder httpRequest = this.httpSender.post(config.url())
            .withHeader("User-Agent", userAgentHeader)
            .withContent("application/x-protobuf", metricsData);
        headers.forEach(httpRequest::withHeader);
        try {
            HttpSender.Response response = httpRequest.send();
            if (!response.isSuccessful()) {
                logger.warn(
                        "Failed to publish metrics (context: {}). Server responded with HTTP status code {} and body {}",
                        getConfigurationContext(), response.code(), response.body());
            }
        }
        catch (Throwable e) {
            logger.warn("Failed to publish metrics (context: {}) ", getConfigurationContext(), e);
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

    /**
     * Get the configuration context.
     * @return A message containing enough information for the log reader to figure out
     * what configuration details may have contributed to the failure.
     */
    private String getConfigurationContext() {
        // While other values may contribute to failures, these two are most common
        return "url=" + config.url() + ", resource-attributes=" + config.resourceAttributes();
    }

}
