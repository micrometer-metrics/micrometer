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

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;

import java.util.Locale;

/**
 * An implementation of {@link OtlpMetricsSender} that uses gRPC (OTLP/gRPC). Preferred
 * over HTTP for in-cluster communication (lower overhead, HTTP/2 multiplexing).
 *
 * <p>
 * The {@link ManagedChannel} is provided by the caller, allowing full control over TLS,
 * keepalive, and transport implementation.
 *
 * <p>
 * Default OTLP/gRPC endpoint: {@code localhost:4317}
 *
 */
public class OtlpGrpcMetricsSender implements OtlpMetricsSender {

    private final MetricsServiceGrpc.MetricsServiceBlockingStub baseStub;

    /**
     * Creates a sender using the provided gRPC channel.
     * @param channel gRPC managed channel pointing to the OTLP collector
     */
    public OtlpGrpcMetricsSender(ManagedChannel channel) {
        this.baseStub = MetricsServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Send a batch of OTLP Protobuf format metrics to an OTLP gRPC receiver.
     * @param request metrics request to publish
     * @throws Exception when there is an exception in sending the metrics; the caller
     * should handle this in some way such as logging the exception
     */
    @Override
    public void send(Request request) throws Exception {
        Metadata headers = new Metadata();
        request.getHeaders().forEach((key, value) -> {
            String normalizedKey = key.toLowerCase(Locale.ROOT);
            if (normalizedKey.endsWith("-bin")) {
                throw new IllegalArgumentException("Header key '" + key
                        + "' ends with '-bin', which requires a binary marshaller and is not supported. "
                        + "Remove or rename this header.");
            }
            headers.put(Metadata.Key.of(normalizedKey, Metadata.ASCII_STRING_MARSHALLER), value);
        });

        MetricsServiceGrpc.MetricsServiceBlockingStub stub = this.baseStub
            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

        if (request.getCompressionMode() == CompressionMode.GZIP) {
            stub = stub.withCompression("gzip");
        }

        ExportMetricsServiceRequest grpcRequest = ExportMetricsServiceRequest.parseFrom(request.getMetricsData());
        stub.export(grpcRequest);
    }

}
