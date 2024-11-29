/*
 * Copyright 2022 the original author or authors.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.protobuf.SimpleRequest;
import io.grpc.testing.protobuf.SimpleResponse;
import io.grpc.testing.protobuf.SimpleServiceGrpc;
import io.grpc.testing.protobuf.SimpleServiceGrpc.SimpleServiceBlockingStub;
import io.grpc.testing.protobuf.SimpleServiceGrpc.SimpleServiceFutureStub;
import io.grpc.testing.protobuf.SimpleServiceGrpc.SimpleServiceImplBase;
import io.grpc.testing.protobuf.SimpleServiceGrpc.SimpleServiceStub;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.grpc.GrpcObservationDocumentation.GrpcClientEvents;
import io.micrometer.core.instrument.binder.grpc.GrpcObservationDocumentation.GrpcServerEvents;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.Observation.Event;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.ObservationTextPublisher;
import io.micrometer.observation.tck.ObservationContextAssert;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;

/**
 * Test for {@link ObservationGrpcServerInterceptor} and
 * {@link ObservationGrpcClientInterceptor}.
 *
 * @author Tadaya Tsuyukubo
 */
class GrpcObservationTest {

    Server server;

    ManagedChannel channel;

    ContextAndEventHoldingObservationHandler<GrpcServerObservationContext> serverHandler;

    ContextAndEventHoldingObservationHandler<GrpcClientObservationContext> clientHandler;

    // tag::setup[]
    ObservationGrpcServerInterceptor serverInterceptor;

    ObservationGrpcClientInterceptor clientInterceptor;

    // end::setup[]

    TestObservationRegistry observationRegistry = TestObservationRegistry.create();

    @BeforeEach
    void setUp() {
        serverHandler = new ContextAndEventHoldingObservationHandler<>(GrpcServerObservationContext.class);
        clientHandler = new ContextAndEventHoldingObservationHandler<>(GrpcClientObservationContext.class);

        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        observationRegistry.observationConfig()
            .observationHandler(new ObservationTextPublisher())
            .observationHandler(new DefaultMeterObservationHandler(meterRegistry))
            .observationHandler(serverHandler)
            .observationHandler(clientHandler);

        // tag::setup_2[]
        this.serverInterceptor = new ObservationGrpcServerInterceptor(observationRegistry);
        this.clientInterceptor = new ObservationGrpcClientInterceptor(observationRegistry);
        // end::setup_2[]
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

    @Nested
    class WithEchoService {

        @BeforeEach
        void setUpEchoService() throws Exception {
            // tag::example[]
            EchoService echoService = new EchoService();
            server = InProcessServerBuilder.forName("sample")
                .addService(echoService)
                .intercept(new ServerHeaderInterceptor())
                .intercept(serverInterceptor)
                .build();
            server.start();

            channel = InProcessChannelBuilder.forName("sample")
                .intercept(new ClientHeaderInterceptor(), clientInterceptor)
                .build();
            // end::example[]
        }

        @Test
        void unaryRpc() {
            // tag::result[]
            SimpleServiceBlockingStub stub = SimpleServiceGrpc.newBlockingStub(channel);

            SimpleRequest request = SimpleRequest.newBuilder().setRequestMessage("Hello").build();
            SimpleResponse response = stub.unaryRpc(request);
            assertThat(response.getResponseMessage()).isEqualTo("Hello");
            // end::result[]

            verifyServerContext("grpc.testing.SimpleService", "UnaryRpc", "grpc.testing.SimpleService/UnaryRpc",
                    MethodType.UNARY);
            verifyClientContext("grpc.testing.SimpleService", "UnaryRpc", "grpc.testing.SimpleService/UnaryRpc",
                    MethodType.UNARY);
            assertThat(serverHandler.getContext().getStatusCode()).isEqualTo(Code.OK);
            assertThat(clientHandler.getContext().getStatusCode()).isEqualTo(Code.OK);
            assertThat(serverHandler.getEvents()).containsExactly(GrpcServerEvents.MESSAGE_RECEIVED,
                    GrpcServerEvents.MESSAGE_SENT);
            assertThat(clientHandler.getEvents()).containsExactly(GrpcClientEvents.MESSAGE_SENT,
                    GrpcClientEvents.MESSAGE_RECEIVED);
            // tag::assertion[]
            assertThat(observationRegistry).hasAnObservation(observationContextAssert -> {
                observationContextAssert.hasNameEqualTo("grpc.client");
                assertCommonKeyValueNames(observationContextAssert);
            }).hasAnObservation(observationContextAssert -> {
                observationContextAssert.hasNameEqualTo("grpc.server");
                assertCommonKeyValueNames(observationContextAssert);
            });
            // end::assertion[]
            verifyHeaders();
        }

        @Test
        void unaryRpcAsync() {
            SimpleServiceFutureStub stub = SimpleServiceGrpc.newFutureStub(channel);
            List<String> messages = new ArrayList<>();
            List<String> responses = Collections.synchronizedList(new ArrayList<>());
            List<ListenableFuture<SimpleResponse>> futures = new ArrayList<>();
            int count = 40;
            for (int i = 0; i < count; i++) {
                String message = "Hello-" + i;
                messages.add(message);
                SimpleRequest request = SimpleRequest.newBuilder().setRequestMessage(message).build();
                ListenableFuture<SimpleResponse> future = stub.unaryRpc(request);
                Futures.addCallback(future, new FutureCallback<>() {
                    @Override
                    public void onSuccess(SimpleResponse result) {
                        responses.add(result.getResponseMessage());
                    }

                    @Override
                    public void onFailure(Throwable t) {

                    }
                }, Executors.newCachedThreadPool());
                futures.add(future);
            }

            await().until(() -> futures.stream().allMatch(Future::isDone));
            assertThat(responses).hasSize(count).containsExactlyInAnyOrderElementsOf(messages);
            assertClientAndServerObservations();
            verifyHeaders();
        }

        @Test
        void clientStreamingRpc() {
            SimpleServiceStub asyncStub = SimpleServiceGrpc.newStub(channel);

            List<String> messages = new ArrayList<>();
            AtomicBoolean completed = new AtomicBoolean();
            StreamObserver<SimpleResponse> responseObserver = createResponseObserver(messages, completed);

            SimpleRequest request1 = SimpleRequest.newBuilder().setRequestMessage("Hello-1").build();
            SimpleRequest request2 = SimpleRequest.newBuilder().setRequestMessage("Hello-2").build();
            StreamObserver<SimpleRequest> requestObserver = asyncStub.clientStreamingRpc(responseObserver);

            verifyClientContext("grpc.testing.SimpleService", "ClientStreamingRpc",
                    "grpc.testing.SimpleService/ClientStreamingRpc", MethodType.CLIENT_STREAMING);

            requestObserver.onNext(request1);
            assertThat(clientHandler.getEvents()).containsExactly(GrpcClientEvents.MESSAGE_SENT);
            assertThat(clientHandler.getContext().getStatusCode()).isNull();

            requestObserver.onNext(request2);
            assertThat(clientHandler.getEvents()).containsExactly(GrpcClientEvents.MESSAGE_SENT,
                    GrpcClientEvents.MESSAGE_SENT);
            assertThat(clientHandler.getContext().getStatusCode()).isNull();

            requestObserver.onCompleted();
            await().untilTrue(completed);

            assertThat(messages).containsExactly("Hello-1,Hello-2");
            assertThat(clientHandler.getEvents()).containsExactly(GrpcClientEvents.MESSAGE_SENT,
                    GrpcClientEvents.MESSAGE_SENT, GrpcClientEvents.MESSAGE_RECEIVED);
            assertThat(clientHandler.getContext().getStatusCode()).isEqualTo(Code.OK);
            assertThat(serverHandler.getEvents()).containsExactly(GrpcServerEvents.MESSAGE_RECEIVED,
                    GrpcServerEvents.MESSAGE_RECEIVED, GrpcServerEvents.MESSAGE_SENT);

            verifyServerContext("grpc.testing.SimpleService", "ClientStreamingRpc",
                    "grpc.testing.SimpleService/ClientStreamingRpc", MethodType.CLIENT_STREAMING);
            assertThat(serverHandler.getContext().getStatusCode()).isEqualTo(Code.OK);
            assertClientAndServerObservations();
            verifyHeaders();
        }

        @Test
        void serverStreamingRpc() {
            // Use async stub since blocking stub cannot detect the server side completion
            SimpleServiceStub asyncStub = SimpleServiceGrpc.newStub(channel);

            List<String> messages = new ArrayList<>();
            AtomicBoolean completed = new AtomicBoolean();
            StreamObserver<SimpleResponse> responseObserver = createResponseObserver(messages, completed);

            SimpleRequest request = SimpleRequest.newBuilder().setRequestMessage("Hello").build();
            asyncStub.serverStreamingRpc(request, responseObserver);

            await().untilTrue(completed);

            assertThat(messages).containsExactly("Hello-1", "Hello-2");

            // server side has finished all processing
            verifyServerContext("grpc.testing.SimpleService", "ServerStreamingRpc",
                    "grpc.testing.SimpleService/ServerStreamingRpc", MethodType.SERVER_STREAMING);
            assertThat(serverHandler.getContext().getStatusCode()).isEqualTo(Code.OK);
            assertThat(serverHandler.getEvents()).containsExactly(GrpcServerEvents.MESSAGE_RECEIVED,
                    GrpcServerEvents.MESSAGE_SENT, GrpcServerEvents.MESSAGE_SENT);

            // verify client side before retrieving the result
            verifyClientContext("grpc.testing.SimpleService", "ServerStreamingRpc",
                    "grpc.testing.SimpleService/ServerStreamingRpc", MethodType.SERVER_STREAMING);
            assertThat(clientHandler.getContext().getStatusCode()).isEqualTo(Code.OK);
            assertThat(clientHandler.getEvents()).containsExactly(GrpcClientEvents.MESSAGE_SENT,
                    GrpcClientEvents.MESSAGE_RECEIVED, GrpcClientEvents.MESSAGE_RECEIVED);
            assertClientAndServerObservations();
            verifyHeaders();
        }

        @Test
        void bidiStreamingRpc() {
            SimpleServiceStub asyncStub = SimpleServiceGrpc.newStub(channel);

            List<String> messages = new ArrayList<>();
            AtomicBoolean completed = new AtomicBoolean();
            StreamObserver<SimpleResponse> responseObserver = createResponseObserver(messages, completed);

            SimpleRequest request1 = SimpleRequest.newBuilder().setRequestMessage("Hello-1").build();
            SimpleRequest request2 = SimpleRequest.newBuilder().setRequestMessage("Hello-2").build();
            StreamObserver<SimpleRequest> requestObserver = asyncStub.bidiStreamingRpc(responseObserver);

            requestObserver.onNext(request1);
            await().until(() -> messages.size() == 2);
            assertThat(messages).containsExactly("Hello-1-A", "Hello-1-B");
            messages.clear();

            verifyClientContext("grpc.testing.SimpleService", "BidiStreamingRpc",
                    "grpc.testing.SimpleService/BidiStreamingRpc", MethodType.BIDI_STREAMING);
            verifyServerContext("grpc.testing.SimpleService", "BidiStreamingRpc",
                    "grpc.testing.SimpleService/BidiStreamingRpc", MethodType.BIDI_STREAMING);

            assertThat(serverHandler.getContext().getStatusCode()).isNull();
            assertThat(clientHandler.getContext().getStatusCode()).isNull();
            assertThat(serverHandler.getEvents()).containsExactly(GrpcServerEvents.MESSAGE_RECEIVED,
                    GrpcServerEvents.MESSAGE_SENT, GrpcServerEvents.MESSAGE_SENT);
            assertThat(clientHandler.getEvents()).containsExactly(GrpcClientEvents.MESSAGE_SENT,
                    GrpcClientEvents.MESSAGE_RECEIVED, GrpcClientEvents.MESSAGE_RECEIVED);

            requestObserver.onNext(request2);
            await().until(() -> messages.size() == 2);
            assertThat(messages).containsExactly("Hello-2-A", "Hello-2-B");

            assertThat(serverHandler.getContext().getStatusCode()).isNull();
            assertThat(clientHandler.getContext().getStatusCode()).isNull();
            assertThat(serverHandler.getEvents()).containsExactly(GrpcServerEvents.MESSAGE_RECEIVED,
                    GrpcServerEvents.MESSAGE_SENT, GrpcServerEvents.MESSAGE_SENT, GrpcServerEvents.MESSAGE_RECEIVED,
                    GrpcServerEvents.MESSAGE_SENT, GrpcServerEvents.MESSAGE_SENT);
            assertThat(clientHandler.getEvents()).containsExactly(GrpcClientEvents.MESSAGE_SENT,
                    GrpcClientEvents.MESSAGE_RECEIVED, GrpcClientEvents.MESSAGE_RECEIVED, GrpcClientEvents.MESSAGE_SENT,
                    GrpcClientEvents.MESSAGE_RECEIVED, GrpcClientEvents.MESSAGE_RECEIVED);

            requestObserver.onCompleted();
            await().untilTrue(completed);

            assertThat(serverHandler.getContext().getStatusCode()).isEqualTo(Code.OK);
            assertThat(clientHandler.getContext().getStatusCode()).isEqualTo(Code.OK);
            assertClientAndServerObservations();
            verifyHeaders();
        }

        private StreamObserver<SimpleResponse> createResponseObserver(List<String> messages, AtomicBoolean completed) {
            return new StreamObserver<>() {

                @Override
                public void onNext(SimpleResponse value) {
                    messages.add(value.getResponseMessage());
                }

                @Override
                public void onError(Throwable t) {
                    throw new RuntimeException("Encountered error", t);
                }

                @Override
                public void onCompleted() {
                    completed.set(true);
                }
            };
        }

        private void verifyHeaders() {
            assertThat(clientHandler.getContext().getCarrier().containsKey(ClientHeaderInterceptor.CLIENT_KEY))
                .isTrue();
            assertThat(clientHandler.getContext().getHeaders().containsKey(ServerHeaderInterceptor.SERVER_KEY))
                .isTrue();
            assertThat(serverHandler.getContext().getCarrier().containsKey(ClientHeaderInterceptor.CLIENT_KEY))
                .isTrue();
            assertThat(serverHandler.getContext().getHeaders().containsKey(ServerHeaderInterceptor.SERVER_KEY))
                .isTrue();
        }

    }

    private void assertClientAndServerObservations() {
        assertThat(observationRegistry).hasAnObservation(observationContextAssert -> {
            observationContextAssert.hasNameEqualTo("grpc.client");
            assertCommonKeyValueNames(observationContextAssert);
        }).hasAnObservation(observationContextAssert -> {
            observationContextAssert.hasNameEqualTo("grpc.server");
            assertCommonKeyValueNames(observationContextAssert);
        });
    }

    @Nested
    class WithExceptionService {

        @BeforeEach
        void setUpExceptionService() throws Exception {
            ExceptionService exceptionService = new ExceptionService();
            server = InProcessServerBuilder.forName("exception")
                .addService(exceptionService)
                .intercept(new ServerHeaderInterceptor())
                .intercept(serverInterceptor)
                .build();
            server.start();

            channel = InProcessChannelBuilder.forName("exception")
                .intercept(new ClientHeaderInterceptor(), clientInterceptor)
                .build();
        }

        @Test
        void unaryRpcFailure() {
            SimpleServiceBlockingStub stub = SimpleServiceGrpc.newBlockingStub(channel);

            SimpleRequest request = SimpleRequest.newBuilder().setRequestMessage("Hello").build();
            assertThatExceptionOfType(StatusRuntimeException.class).isThrownBy(() -> stub.unaryRpc(request));

            verifyServerContext("grpc.testing.SimpleService", "UnaryRpc", "grpc.testing.SimpleService/UnaryRpc",
                    MethodType.UNARY);
            verifyClientContext("grpc.testing.SimpleService", "UnaryRpc", "grpc.testing.SimpleService/UnaryRpc",
                    MethodType.UNARY);
            assertThat(serverHandler.getContext().getStatusCode()).isNull();
            assertThat(clientHandler.getContext().getStatusCode()).isEqualTo(Code.UNKNOWN);
            assertThat(serverHandler.getEvents()).containsExactly(GrpcServerEvents.MESSAGE_RECEIVED);
            assertThat(clientHandler.getEvents()).containsExactly(GrpcClientEvents.MESSAGE_SENT);
            assertServerErrorObservation();
        }

        @Test
        void clientStreamingRpcFailure() {
            SimpleServiceStub asyncStub = SimpleServiceGrpc.newStub(channel);

            AtomicBoolean errored = new AtomicBoolean();
            StreamObserver<SimpleResponse> responseObserver = createResponseObserver(errored);

            asyncStub.clientStreamingRpc(responseObserver);

            await().untilTrue(errored);

            verifyClientContext("grpc.testing.SimpleService", "ClientStreamingRpc",
                    "grpc.testing.SimpleService/ClientStreamingRpc", MethodType.CLIENT_STREAMING);
            verifyServerContext("grpc.testing.SimpleService", "ClientStreamingRpc",
                    "grpc.testing.SimpleService/ClientStreamingRpc", MethodType.CLIENT_STREAMING);
            assertThat(clientHandler.getContext().getStatusCode()).isEqualTo(Code.UNKNOWN);
            assertThat(serverHandler.getContext().getStatusCode()).isNull();
            assertThat(clientHandler.getEvents()).isEmpty();
            assertThat(serverHandler.getEvents()).isEmpty();
            assertServerErrorObservation();
        }

        @Test
        void serverStreamingRpcFailure() {
            // With blocking stub, it cannot detect server complete. So, use async stub.
            SimpleServiceStub asyncStub = SimpleServiceGrpc.newStub(channel);

            AtomicBoolean errored = new AtomicBoolean();
            StreamObserver<SimpleResponse> responseObserver = createResponseObserver(errored);

            SimpleRequest request = SimpleRequest.newBuilder().setRequestMessage("Hello").build();
            asyncStub.serverStreamingRpc(request, responseObserver);

            await().untilTrue(errored);

            verifyClientContext("grpc.testing.SimpleService", "ServerStreamingRpc",
                    "grpc.testing.SimpleService/ServerStreamingRpc", MethodType.SERVER_STREAMING);
            verifyServerContext("grpc.testing.SimpleService", "ServerStreamingRpc",
                    "grpc.testing.SimpleService/ServerStreamingRpc", MethodType.SERVER_STREAMING);
            assertThat(clientHandler.getContext().getStatusCode()).isEqualTo(Code.UNKNOWN);
            assertThat(serverHandler.getContext().getStatusCode()).isNull();
            assertThat(clientHandler.getEvents()).containsExactly(GrpcClientEvents.MESSAGE_SENT);
            assertThat(serverHandler.getEvents()).containsExactly(GrpcServerEvents.MESSAGE_RECEIVED);
            assertServerErrorObservation();
        }

        @Test
        void bidiStreamingRpcFailure() {
            SimpleServiceStub asyncStub = SimpleServiceGrpc.newStub(channel);

            AtomicBoolean errored = new AtomicBoolean();
            StreamObserver<SimpleResponse> responseObserver = createResponseObserver(errored);

            // the call to the service fails, so don't need to send message from client
            asyncStub.bidiStreamingRpc(responseObserver);

            await().untilTrue(errored);

            verifyClientContext("grpc.testing.SimpleService", "BidiStreamingRpc",
                    "grpc.testing.SimpleService/BidiStreamingRpc", MethodType.BIDI_STREAMING);
            verifyServerContext("grpc.testing.SimpleService", "BidiStreamingRpc",
                    "grpc.testing.SimpleService/BidiStreamingRpc", MethodType.BIDI_STREAMING);
            assertThat(clientHandler.getContext().getStatusCode()).isEqualTo(Code.UNKNOWN);
            assertThat(serverHandler.getContext().getStatusCode()).isNull();
            assertThat(clientHandler.getEvents()).isEmpty();
            assertThat(serverHandler.getEvents()).isEmpty();
            assertServerErrorObservation();
        }

        private void assertServerErrorObservation() {
            assertThat(observationRegistry).hasAnObservation(observationContextAssert -> {
                observationContextAssert.hasNameEqualTo("grpc.server").hasError();
                assertCommonKeyValueNames(observationContextAssert);
            });
        }

        private StreamObserver<SimpleResponse> createResponseObserver(AtomicBoolean errored) {
            return new StreamObserver<>() {
                @Override
                public void onNext(SimpleResponse value) {
                    throw new RuntimeException("Should not receive any message");
                }

                @Override
                public void onError(Throwable t) {
                    errored.set(true);
                }

                @Override
                public void onCompleted() {
                    throw new RuntimeException("Should not be successfully completed");
                }
            };
        }

    }

    @Nested
    class WithObservationAwareInterceptor {

        ObservationAwareServerInterceptor scopeAwareServerInterceptor;

        @BeforeEach
        void setCustomInterceptor() throws Exception {
            scopeAwareServerInterceptor = new ObservationAwareServerInterceptor(observationRegistry);

            EchoService echoService = new EchoService();
            server = InProcessServerBuilder.forName("sample")
                .addService(echoService)
                .intercept(scopeAwareServerInterceptor)
                .intercept(serverInterceptor)
                .build();
            server.start();

            channel = InProcessChannelBuilder.forName("sample").intercept(clientInterceptor).build();
        }

        @Test
        void observationShouldBeCapturedByInterceptor() {
            SimpleServiceBlockingStub stub = SimpleServiceGrpc.newBlockingStub(channel);

            SimpleRequest request = SimpleRequest.newBuilder().setRequestMessage("Hello").build();
            stub.unaryRpc(request);

            // await until server side processing finishes, otherwise context name might
            // not be populated.
            await().until(serverHandler::isContextStopped);

            assertThat(scopeAwareServerInterceptor.lastObservation).isNotNull().satisfies((observation -> {
                assertThat(observation.getContext().getContextualName())
                    .isEqualTo("grpc.testing.SimpleService/UnaryRpc");
            }));
        }

    }

    @Nested
    class ClientInterruption {

        private ClientInterruptionAwareService service = new ClientInterruptionAwareService();

        @BeforeEach
        void setUpService() throws Exception {
            server = InProcessServerBuilder.forName("sample").addService(service).intercept(serverInterceptor).build();
            server.start();

            channel = InProcessChannelBuilder.forName("sample").intercept(clientInterceptor).build();
        }

        @Test
        void cancel() {
            SimpleServiceFutureStub stub = SimpleServiceGrpc.newFutureStub(channel);
            SimpleRequest request = SimpleRequest.newBuilder().setRequestMessage("Hello").build();
            ListenableFuture<SimpleResponse> future = stub.unaryRpc(request);

            await().untilTrue(this.service.requestReceived);
            future.cancel(true);
            this.service.requestInterrupted.set(true);
            await().until(future::isDone);
            assertThat(future.isCancelled()).isTrue();
            assertThat(observationRegistry)
                .hasAnObservation(observationContextAssert -> observationContextAssert.hasNameEqualTo("grpc.client")
                    .hasLowCardinalityKeyValue("grpc.status_code", "CANCELLED"))
                .hasAnObservation(observationContextAssert -> observationContextAssert.hasNameEqualTo("grpc.server")
                    .satisfies(observation -> assertThat(observation).isInstanceOfSatisfying(
                            GrpcServerObservationContext.class,
                            context -> assertThat(context.isCancelled()).isTrue())));
            assertThat(serverHandler.getEvents()).contains(GrpcServerEvents.CANCELLED);
        }

        @Test
        void shutdown() {
            SimpleServiceFutureStub stub = SimpleServiceGrpc.newFutureStub(channel);
            SimpleRequest request = SimpleRequest.newBuilder().setRequestMessage("Hello").build();
            ListenableFuture<SimpleResponse> future = stub.unaryRpc(request);

            await().untilTrue(this.service.requestReceived);
            channel.shutdownNow(); // shutdown client while server is processing
            this.service.requestInterrupted.set(true);
            await().until(channel::isTerminated);
            await().until(future::isDone);
            assertThat(observationRegistry)
                .hasAnObservation(observationContextAssert -> observationContextAssert.hasNameEqualTo("grpc.client")
                    .hasLowCardinalityKeyValue("grpc.status_code", "UNAVAILABLE"));
            assertThat(serverHandler.getEvents()).contains(GrpcServerEvents.CANCELLED);
        }

    }

    // perform server context verification on basic information
    void verifyServerContext(String serviceName, String methodName, String contextualName, MethodType methodType) {
        assertThat(serverHandler.getContext()).isNotNull().satisfies((serverContext) -> {
            assertThat(serverContext).isNotNull();
            assertThat(serverContext.getServiceName()).isEqualTo(serviceName);
            assertThat(serverContext.getMethodName()).isEqualTo(methodName);
            assertThat(serverContext.getFullMethodName()).isEqualTo(contextualName);
            assertThat(serverContext.getMethodType()).isEqualTo(methodType);
            assertThat(serverContext.getAuthority()).isEqualTo("localhost");
            assertThat(serverContext.getPeerName()).isEqualTo("localhost");
            assertThat(serverContext.getPeerPort()).isEqualTo(-1);
        });
    }

    // perform client context verification on basic information
    void verifyClientContext(String serviceName, String methodName, String contextualName, MethodType methodType) {
        assertThat(clientHandler.getContext()).isNotNull().satisfies((clientContext) -> {
            assertThat(clientContext).isNotNull();
            assertThat(clientContext.getServiceName()).isEqualTo(serviceName);
            assertThat(clientContext.getMethodName()).isEqualTo(methodName);
            assertThat(clientContext.getFullMethodName()).isEqualTo(contextualName);
            assertThat(clientContext.getMethodType()).isEqualTo(methodType);
            assertThat(clientContext.getAuthority()).isEqualTo("localhost");
            assertThat(clientContext.getPeerName()).isEqualTo("localhost");
            assertThat(clientContext.getPeerPort()).isEqualTo(-1);
        });
    }

    void assertCommonKeyValueNames(ObservationContextAssert<?> observationContextAssert) {
        observationContextAssert
            .hasLowCardinalityKeyValueWithKey(GrpcObservationDocumentation.LowCardinalityKeyNames.METHOD.asString())
            .hasLowCardinalityKeyValueWithKey(
                    GrpcObservationDocumentation.LowCardinalityKeyNames.METHOD_TYPE.asString())
            .hasLowCardinalityKeyValueWithKey(GrpcObservationDocumentation.LowCardinalityKeyNames.SERVICE.asString())
            .hasLowCardinalityKeyValueWithKey(
                    GrpcObservationDocumentation.LowCardinalityKeyNames.STATUS_CODE.asString());
    }

    // GRPC service extending SimpleService and provides echo implementation.
    static class EchoService extends SimpleServiceImplBase {

        // echo the request message
        @Override
        public void unaryRpc(SimpleRequest request, StreamObserver<SimpleResponse> responseObserver) {
            SimpleResponse response = SimpleResponse.newBuilder()
                .setResponseMessage(request.getRequestMessage())
                .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        // returns concatenated message
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

        // returns two messages
        @Override
        public void serverStreamingRpc(SimpleRequest request, StreamObserver<SimpleResponse> responseObserver) {
            String message = request.getRequestMessage();
            responseObserver.onNext(SimpleResponse.newBuilder().setResponseMessage(message + "-1").build());
            responseObserver.onNext(SimpleResponse.newBuilder().setResponseMessage(message + "-2").build());
            responseObserver.onCompleted();
        }

        // returns two messages per received message
        @Override
        public StreamObserver<SimpleRequest> bidiStreamingRpc(StreamObserver<SimpleResponse> responseObserver) {
            return new StreamObserver<>() {

                @Override
                public void onNext(SimpleRequest value) {
                    String message = value.getRequestMessage();
                    responseObserver.onNext(SimpleResponse.newBuilder().setResponseMessage(message + "-A").build());
                    responseObserver.onNext(SimpleResponse.newBuilder().setResponseMessage(message + "-B").build());
                }

                @Override
                public void onError(Throwable t) {
                    throw new RuntimeException("Encountered error", t);
                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
        }

    }

    // Default implementation in the parent class throws UNIMPLEMENTED error
    static class ExceptionService extends SimpleServiceImplBase {

        @Override
        public void unaryRpc(SimpleRequest request, StreamObserver<SimpleResponse> responseObserver) {
            throw new IllegalStateException("Boom!");
        }

        @Override
        public StreamObserver<SimpleRequest> clientStreamingRpc(StreamObserver<SimpleResponse> responseObserver) {
            throw new IllegalStateException("Boom!");
        }

        @Override
        public void serverStreamingRpc(SimpleRequest request, StreamObserver<SimpleResponse> responseObserver) {
            throw new IllegalStateException("Boom!");
        }

        @Override
        public StreamObserver<SimpleRequest> bidiStreamingRpc(StreamObserver<SimpleResponse> responseObserver) {
            throw new IllegalStateException("Boom!");
        }

    }

    static class ClientInterruptionAwareService extends SimpleServiceImplBase {

        AtomicBoolean requestReceived = new AtomicBoolean();

        AtomicBoolean requestInterrupted = new AtomicBoolean();

        @Override
        public void unaryRpc(SimpleRequest request, StreamObserver<SimpleResponse> responseObserver) {
            this.requestReceived.set(true);
            SimpleResponse response = SimpleResponse.newBuilder()
                .setResponseMessage(request.getRequestMessage())
                .build();
            await().untilTrue(this.requestInterrupted);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

    }

    // Hold reference to the Context and Events happened in ObservationHandler
    static class ContextAndEventHoldingObservationHandler<T extends Observation.Context>
            implements ObservationHandler<T> {

        private final AtomicReference<T> contextHolder = new AtomicReference<>();

        private final List<Event> events = new ArrayList<>();

        private final Class<T> contextClass;

        private boolean contextStopped;

        ContextAndEventHoldingObservationHandler(Class<T> contextClass) {
            this.contextClass = contextClass;
        }

        @Override
        public boolean supportsContext(Context context) {
            if (this.contextClass.isInstance(context)) {
                this.contextHolder.set(this.contextClass.cast(context));
                return true;
            }
            return false;
        }

        @Override
        public void onEvent(Event event, T context) {
            this.events.add(event);
        }

        @Nullable
        T getContext() {
            return this.contextHolder.get();
        }

        @Override
        public void onStop(T context) {
            if (context.equals(this.contextHolder.get())) {
                this.contextStopped = true;
            }
        }

        List<Event> getEvents() {
            return this.events;
        }

        public boolean isContextStopped() {
            return this.contextStopped;
        }

    }

    static class ClientHeaderInterceptor implements ClientInterceptor {

        private static final Metadata.Key<String> CLIENT_KEY = Metadata.Key.of("client",
                Metadata.ASCII_STRING_MARSHALLER);

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                CallOptions callOptions, Channel next) {
            ClientCall<ReqT, RespT> call = next.newCall(method, callOptions);
            return new SimpleForwardingClientCall<>(call) {
                @Override
                public void start(ClientCall.Listener<RespT> responseListener, Metadata headers) {
                    headers.put(CLIENT_KEY, "client-request");
                    super.start(responseListener, headers);
                }

            };
        }

    }

    static class ServerHeaderInterceptor implements ServerInterceptor {

        private static final Metadata.Key<String> SERVER_KEY = Metadata.Key.of("server",
                Metadata.ASCII_STRING_MARSHALLER);

        @Override
        public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
                ServerCallHandler<ReqT, RespT> next) {
            SimpleForwardingServerCall<ReqT, RespT> serverCall = new SimpleForwardingServerCall<>(call) {
                @Override
                public void sendHeaders(Metadata headers) {
                    headers.put(SERVER_KEY, "server-response");
                    super.sendHeaders(headers);
                }

            };
            return next.startCall(serverCall, headers);
        }

    }

    // Hold reference to last intercepted Observation
    static class ObservationAwareServerInterceptor implements ServerInterceptor {

        Observation lastObservation;

        private final ObservationRegistry observationRegistry;

        private ObservationAwareServerInterceptor(ObservationRegistry observationRegistry) {
            this.observationRegistry = observationRegistry;
        }

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
                ServerCallHandler<ReqT, RespT> next) {
            this.lastObservation = observationRegistry.getCurrentObservation();
            return next.startCall(call, headers);
        }

    }

}
