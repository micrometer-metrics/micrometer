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

import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * This is responsible for sending OTLP protobuf format metrics to a compatible location.
 * Specific implementations can use different transports or clients for sending the
 * metrics.
 *
 * @since 1.15.0
 */
public interface OtlpMetricsSender {

    /**
     * Send a batch of OTLP Protobuf format metrics to an OTLP receiver.
     * @param request metrics request to publish
     * @throws Exception when there is an exception in sending the metrics; the caller
     * should handle this in some way such as logging the exception
     */
    void send(Request request) throws Exception;

    /**
     * Immutable representation of a payload of metrics to use with an
     * {@link OtlpMetricsSender}.
     */
    class Request {

        private final @Nullable String address;

        private final Map<String, String> headers;

        private final byte[] metricsData;

        /**
         * Represents a payload of metrics to be sent.
         * @param address where to send the metrics
         * @param headers metadata to send as headers with the metrics data
         * @param metricsData OTLP protobuf encoded batch of metrics
         */
        private Request(@Nullable String address, Map<String, String> headers, byte[] metricsData) {
            this.address = address;
            this.headers = headers;
            this.metricsData = metricsData;
        }

        public @Nullable String getAddress() {
            return address;
        }

        public byte[] getMetricsData() {
            return metricsData;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        @Override
        public String toString() {
            return "OtlpMetricsSender.Request for address: " + address + ", headers: " + headers + ", metricsData: "
                    + new String(metricsData, StandardCharsets.UTF_8);
        }

        /**
         * Get a builder for a request.
         * @param metricsData OTLP protobuf encoded batch of metrics
         * @return builder
         */
        public static Builder builder(byte[] metricsData) {
            return new Builder(metricsData);
        }

        public static class Builder {

            private final byte[] metricsData;

            private @Nullable String address;

            private Map<String, String> headers = Collections.emptyMap();

            private Builder(byte[] metricsData) {
                this.metricsData = Objects.requireNonNull(metricsData);
            }

            public Builder address(String address) {
                this.address = address;
                return this;
            }

            public Builder headers(Map<String, String> headers) {
                this.headers = headers;
                return this;
            }

            public Request build() {
                return new Request(address, headers, metricsData);
            }

        }

    }

}
