/*
 * Copyright 2023 the original author or authors.
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

package io.micrometer.core.instrument.binder.grpc;

import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.*;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.protobuf.SimpleRequest;
import io.grpc.testing.protobuf.SimpleResponse;
import io.grpc.testing.protobuf.SimpleServiceGrpc;
import io.grpc.testing.protobuf.SimpleServiceGrpc.SimpleServiceFutureStub;
import io.grpc.testing.protobuf.SimpleServiceGrpc.SimpleServiceImplBase;
import io.grpc.testing.protobuf.SimpleServiceGrpc.SimpleServiceStub;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

/**
 * @author Tadaya Tsuyukubo
 */
class GrpcAsyncTest {

    static final Metadata.Key<String> REQUEST_ID_KEY = Metadata.Key.of("request-id", Metadata.ASCII_STRING_MARSHALLER);

    Server server;

    ManagedChannel channel;

    ObservationRegistry observationRegistry;

    @BeforeEach
    void setUp() throws Exception {
        this.observationRegistry = ObservationRegistry.create();

        this.server = InProcessServerBuilder.forName("sample")
            .addService(new MyService())
            .intercept(new ObservationGrpcServerInterceptor(observationRegistry))
            .build();
        this.server.start();
    }

    @AfterEach
    void cleanUp() {
        if (this.channel != null) {
            this.channel.shutdownNow();
        }
        if (this.server != null) {
            this.server.shutdownNow();
        }
    }

    @Test
    void simulate_trace_in_async_requests() {
        this.observationRegistry.observationConfig().observationHandler(new StoreRequestIdInScopeObservationHandler());

        this.channel = InProcessChannelBuilder.forName("sample")
            .intercept(new ObservationGrpcClientInterceptor(this.observationRegistry))
            .build();

        // Send requests asynchronously with request-id in metadata.
        // The request-id is stored in threadlocal in server when scope is opened.
        // The main logic retrieves the request-id from threadlocal and includes it as
        // part of the response message.
        // This simulates a tracer with span.
        SimpleServiceFutureStub stub = SimpleServiceGrpc.newFutureStub(this.channel);
        Map<ListenableFuture<SimpleResponse>, String> requestIds = new HashMap<>();
        int max = 40;
        for (int i = 0; i < max; i++) {
            String message = "Hello-" + i;
            SimpleRequest request = SimpleRequest.newBuilder().setRequestMessage(message).build();

            String requestId = "req-" + i;
            Metadata metadata = new Metadata();
            metadata.put(REQUEST_ID_KEY, requestId);
            ListenableFuture<SimpleResponse> future = stub
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                .unaryRpc(request);

            requestIds.put(future, requestId);
        }
        Set<ListenableFuture<SimpleResponse>> futures = requestIds.keySet();
        await().until(() -> futures.stream().allMatch(Future::isDone));
        assertThat(futures).allSatisfy((future) -> {
            // Make sure the request-id in the response message matches with the one sent
            // to server.
            String expectedRequestId = requestIds.get(future);
            assertThat(future.get().getResponseMessage()).contains("request-id=" + expectedRequestId);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void multi_thread_client() throws Exception {
        AtomicReference<Observation> onStart = new AtomicReference<>();
        AtomicReference<Observation> onMessage = new AtomicReference<>();
        AtomicReference<Observation> halfClose = new AtomicReference<>();
        ClientInterceptor clientInterceptor = new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                    CallOptions callOptions, Channel next) {
                return new SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {

                    @Override
                    public void start(Listener<RespT> responseListener, Metadata headers) {
                        onStart.set(observationRegistry.getCurrentObservation());
                        super.start(responseListener, headers);
                    }

                    @Override
                    public void sendMessage(ReqT message) {
                        onMessage.set(observationRegistry.getCurrentObservation());
                        super.sendMessage(message);
                    }

                    @Override
                    public void halfClose() {
                        halfClose.set(observationRegistry.getCurrentObservation());
                        super.halfClose();
                    }
                };
            }
        };

        this.observationRegistry.observationConfig().observationHandler(context -> true);
        this.channel = InProcessChannelBuilder.forName("sample")
            .intercept(clientInterceptor)
            .intercept(new ObservationGrpcClientInterceptor(this.observationRegistry))
            .build();

        SimpleServiceStub asyncStub = SimpleServiceGrpc.newStub(this.channel);
        StreamObserver<SimpleResponse> responseObserver = mock(StreamObserver.class);
        SimpleRequest request = SimpleRequest.newBuilder().setRequestMessage("Hello").build();

        Observation ob = Observation.start("foo", this.observationRegistry);
        ob.observeChecked(() -> {
            StreamObserver<SimpleRequest> requestObserver = asyncStub.clientStreamingRpc(responseObserver);

            // perform onNext on a separate thread
            CountDownLatch onNextDone = new CountDownLatch(1);
            CompletableFuture.runAsync(() -> {
                requestObserver.onNext(request);
                onNextDone.countDown();
            }).join();
            assertThat(onNextDone.await(100, TimeUnit.MILLISECONDS)).isTrue();

            // perform onCompleted on a separate thread
            CountDownLatch onCompletedDone = new CountDownLatch(1);
            CompletableFuture.runAsync(() -> {
                requestObserver.onCompleted();
                onCompletedDone.countDown();
            }).join();
            assertThat(onNextDone.await(100, TimeUnit.MILLISECONDS)).isTrue();
        });

        assertThat(Stream.of(onStart, onMessage, halfClose))
            .allSatisfy((reference) -> assertThat(reference).hasValueSatisfying((value) -> assertThat(value).isNotNull()
                .extracting(Observation::getContext)
                .extracting(Observation.Context::getParentObservation)
                .isSameAs(ob)));
    }

    static class StoreRequestIdInScopeObservationHandler implements ObservationHandler<GrpcServerObservationContext> {

        @Override
        public boolean supportsContext(Context context) {
            return context instanceof GrpcServerObservationContext;
        }

        @Override
        public void onScopeOpened(GrpcServerObservationContext context) {
            String requestId = context.getCarrier().get(REQUEST_ID_KEY);
            assertThat(requestId).isNotNull();
            MyService.requestIdHolder.set(requestId);
        }

        @Override
        public void onScopeClosed(GrpcServerObservationContext context) {
            MyService.requestIdHolder.remove();
        }

    }

    static class MyService extends SimpleServiceImplBase {

        static ThreadLocal<String> requestIdHolder = new ThreadLocal<>();

        @Override
        public void unaryRpc(SimpleRequest request, StreamObserver<SimpleResponse> responseObserver) {
            StringBuilder sb = new StringBuilder();
            sb.append("message=");
            sb.append(request.getRequestMessage());
            sb.append(",request-id=");
            sb.append(requestIdHolder.get());
            sb.append(",thread=");
            sb.append(Thread.currentThread().getId());

            SimpleResponse response = SimpleResponse.newBuilder().setResponseMessage(sb.toString()).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public StreamObserver<SimpleRequest> clientStreamingRpc(StreamObserver<SimpleResponse> responseObserver) {
            return new StreamObserver<>() {
                final List<String> messages = new ArrayList<>();

                @Override
                public void onNext(SimpleRequest value) {
                    this.messages.add(value.getRequestMessage());
                }

                @Override
                public void onError(Throwable t) {
                    throw new RuntimeException("Encountered error", t);
                }

                @Override
                public void onCompleted() {
                    String message = String.join(",", this.messages);
                    responseObserver.onNext(SimpleResponse.newBuilder().setResponseMessage(message).build());
                    responseObserver.onCompleted();
                }
            };
        }

    }

}
