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
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link OtlpGrpcMetricsSender}.
 *
 */
class OtlpGrpcMetricsSenderTests {

    private static final byte[] EMPTY_REQUEST_BYTES = ExportMetricsServiceRequest.getDefaultInstance().toByteArray();

    private static final Metadata.Key<String> TEST_HEADER_KEY = Metadata.Key.of("test-header",
            Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> GRPC_ENCODING_KEY = Metadata.Key.of("grpc-encoding",
            Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> USER_AGENT_KEY = Metadata.Key.of("user-agent",
            Metadata.ASCII_STRING_MARSHALLER);

    private String serverName;

    private Server server;

    private ManagedChannel channel;

    private TestMetricsService testService;

    @BeforeEach
    void setUp() throws Exception {
        this.serverName = UUID.randomUUID().toString();
        this.testService = new TestMetricsService();
        this.server = InProcessServerBuilder.forName(this.serverName)
            .addService(this.testService)
            .intercept(new HeaderCapturingInterceptor(this.testService))
            .directExecutor()
            .build()
            .start();
        this.channel = InProcessChannelBuilder.forName(this.serverName).directExecutor().build();
    }

    @AfterEach
    void tearDown() {
        if (this.channel != null) {
            this.channel.shutdownNow();
        }
        if (this.server != null) {
            this.server.shutdownNow();
        }
    }

    @Test
    void shouldSendMetrics() throws Exception {
        OtlpGrpcMetricsSender sender = new OtlpGrpcMetricsSender(this.channel);
        OtlpMetricsSender.Request request = OtlpMetricsSender.Request.builder(EMPTY_REQUEST_BYTES).build();

        sender.send(request);

        assertThat(this.testService.exportCalled.get()).isTrue();
    }

    @Test
    void shouldTransmitHeaders() throws Exception {
        OtlpGrpcMetricsSender sender = new OtlpGrpcMetricsSender(this.channel);
        OtlpMetricsSender.Request request = OtlpMetricsSender.Request.builder(EMPTY_REQUEST_BYTES)
            .headers(Map.of("test-header", "test-value"))
            .build();

        sender.send(request);

        assertThat(this.testService.capturedHeaders.get()).isNotNull();
        assertThat(this.testService.capturedHeaders.get().get(TEST_HEADER_KEY)).isEqualTo("test-value");
    }

    @Test
    void shouldSendUserAgentHeader() throws Exception {
        OtlpGrpcMetricsSender sender = new OtlpGrpcMetricsSender(this.channel);
        OtlpMetricsSender.Request request = OtlpMetricsSender.Request.builder(EMPTY_REQUEST_BYTES).build();

        sender.send(request);

        assertThat(this.testService.capturedHeaders.get()).isNotNull();
        assertThat(this.testService.capturedHeaders.get().getAll(USER_AGENT_KEY))
            .anyMatch(v -> v.contains("Micrometer-OTLP-Exporter-Java"));
    }

    @Test
    void shouldSendWithGzipCompression() throws Exception {
        OtlpGrpcMetricsSender sender = new OtlpGrpcMetricsSender(this.channel);
        OtlpMetricsSender.Request request = OtlpMetricsSender.Request.builder(EMPTY_REQUEST_BYTES)
            .compressionMode(CompressionMode.GZIP)
            .build();

        sender.send(request);

        assertThat(this.testService.capturedHeaders.get()).isNotNull();
        assertThat(this.testService.capturedHeaders.get().get(GRPC_ENCODING_KEY)).isEqualTo("gzip");
    }

    @Test
    void shouldPropagateGrpcErrors() {
        this.testService.failWith = Status.UNAVAILABLE.asRuntimeException();
        OtlpGrpcMetricsSender sender = new OtlpGrpcMetricsSender(this.channel);
        OtlpMetricsSender.Request request = OtlpMetricsSender.Request.builder(EMPTY_REQUEST_BYTES).build();

        assertThatException().isThrownBy(() -> sender.send(request)).isInstanceOf(StatusRuntimeException.class);
    }

    @Test
    void shouldRejectBinSuffixHeaderKeys() {
        OtlpGrpcMetricsSender sender = new OtlpGrpcMetricsSender(this.channel);
        OtlpMetricsSender.Request request = OtlpMetricsSender.Request.builder(EMPTY_REQUEST_BYTES)
            .headers(Map.of("custom-bin", "value"))
            .build();

        assertThatThrownBy(() -> sender.send(request)).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("custom-bin");
    }

    private static class TestMetricsService extends MetricsServiceGrpc.MetricsServiceImplBase {

        final AtomicBoolean exportCalled = new AtomicBoolean();

        final AtomicReference<Metadata> capturedHeaders = new AtomicReference<>();

        @Nullable StatusRuntimeException failWith;

        @Override
        public void export(ExportMetricsServiceRequest request,
                StreamObserver<ExportMetricsServiceResponse> responseObserver) {
            this.exportCalled.set(true);
            if (this.failWith != null) {
                responseObserver.onError(this.failWith);
            }
            else {
                responseObserver.onNext(ExportMetricsServiceResponse.getDefaultInstance());
                responseObserver.onCompleted();
            }
        }

    }

    private static class HeaderCapturingInterceptor implements ServerInterceptor {

        private final TestMetricsService service;

        HeaderCapturingInterceptor(TestMetricsService service) {
            this.service = service;
        }

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
                ServerCallHandler<ReqT, RespT> next) {
            this.service.capturedHeaders.set(headers);
            return next.startCall(call, headers);
        }

    }

}
