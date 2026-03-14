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

/**
 * Transport protocol to use for OTLP export.
 *
 * @see <a href="https://opentelemetry.io/docs/specs/otel/protocol/exporter/">OTLP
 * Exporter Configuration</a>
 */
public enum OtlpTransportProtocol {

    /**
     * gRPC transport (OTLP/gRPC). Default endpoint: {@code http://localhost:4317}.
     */
    GRPC,

    /**
     * HTTP transport with Protobuf encoding (OTLP/HTTP). Default endpoint:
     * {@code http://localhost:4318/v1/metrics}.
     */
    HTTP_PROTOBUF;

    /**
     * Converts an OpenTelemetry protocol string value to an
     * {@link OtlpTransportProtocol}. Accepts {@code "grpc"} and {@code "http/protobuf"}
     * (case-insensitive). The underscore alias {@code "http_protobuf"} is also accepted
     * as a property value.
     * @param value the protocol string from an OpenTelemetry environment variable or
     * configuration property
     * @return the matching protocol
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static OtlpTransportProtocol fromString(String value) {
        if ("grpc".equalsIgnoreCase(value)) {
            return GRPC;
        }
        if ("http/protobuf".equalsIgnoreCase(value) || "http_protobuf".equalsIgnoreCase(value)) {
            return HTTP_PROTOBUF;
        }
        throw new IllegalArgumentException("Unknown OTLP transport protocol: '" + value
                + "'. Accepted values: 'grpc', 'http/protobuf' (or 'http_protobuf' as a property value)");
    }

}
