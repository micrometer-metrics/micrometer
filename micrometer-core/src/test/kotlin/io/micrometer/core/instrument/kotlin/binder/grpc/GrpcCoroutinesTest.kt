/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.core.instrument.kotlin.binder.grpc

import io.grpc.ManagedChannel
import io.grpc.MethodDescriptor
import io.grpc.Server
import io.grpc.ServerServiceDefinition
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.kotlin.AbstractCoroutineServerImpl
import io.grpc.kotlin.ServerCalls
import io.grpc.stub.annotations.RpcMethod
import io.grpc.testing.protobuf.SimpleRequest
import io.grpc.testing.protobuf.SimpleResponse
import io.grpc.testing.protobuf.SimpleServiceGrpc
import io.micrometer.core.instrument.binder.grpc.ObservationGrpcServerInterceptor
import io.micrometer.core.instrument.kotlin.ObservationCoroutineContextServerInterceptor
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.ObservationTextPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GrpcCoroutinesTest {

    val observationRegistry: ObservationRegistry = ObservationRegistry.create()
    val echoServiceCoroutine: EchoServiceCoroutine = EchoServiceCoroutine(observationRegistry)
    lateinit var server: Server
    lateinit var channel: ManagedChannel

    @BeforeEach
    fun setUp() {
        server = InProcessServerBuilder.forName("sample")
            .intercept(ObservationCoroutineContextServerInterceptor(observationRegistry))
            .intercept(ObservationGrpcServerInterceptor(observationRegistry))
            .addService(echoServiceCoroutine)
            .build()
        server.start()
        channel = InProcessChannelBuilder.forName("sample").build()
    }

    @AfterEach
    fun cleanUp() {
        channel.shutdownNow()
        server.shutdownNow()
    }

    @Test
    fun `unary rpc should propagate observation`() {
        val stub = SimpleServiceGrpc.newBlockingStub(channel)
        val request = SimpleRequest.newBuilder()
            .setRequestMessage("hello")
            .build()
        observationRegistry.observationConfig()
            .observationHandler(ObservationTextPublisher())

        stub.unaryRpc(request)

        assertThat<Observation>(echoServiceCoroutine.lastObservation).isNotNull()
            .extracting { it.context.contextualName }.isEqualTo("grpc.testing.SimpleService/UnaryRpc")
    }

    // This service has the same rpc method that the one defined in SimpleServiceGrpc
    class EchoServiceCoroutine(private val observationRegistry: ObservationRegistry) : AbstractCoroutineServerImpl() {

        var lastObservation: Observation? = null

        @RpcMethod(
            fullMethodName = "${SimpleServiceGrpc.SERVICE_NAME}/UnaryRpc",
            requestType = SimpleRequest::class,
            responseType = SimpleResponse::class,
            methodType = MethodDescriptor.MethodType.UNARY,
        )
        fun unaryRpc(request: SimpleRequest): SimpleResponse {
            lastObservation = observationRegistry.currentObservation
            return SimpleResponse.newBuilder()
                .setResponseMessage(request.getRequestMessage())
                .build()
        }

        override fun bindService(): ServerServiceDefinition {
            return ServerServiceDefinition.builder(SimpleServiceGrpc.SERVICE_NAME)
                .addMethod(
                    ServerCalls.unaryServerMethodDefinition(
                        context = context,
                        descriptor = SimpleServiceGrpc.getUnaryRpcMethod(),
                        implementation = ::unaryRpc,
                    ),
                ).build()
        }
    }
}
