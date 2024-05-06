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
package io.micrometer.core.instrument.kotlin

import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.kotlin.CoroutineContextServerInterceptor
import io.micrometer.observation.ObservationRegistry
import kotlin.coroutines.CoroutineContext

/**
 * This interceptor is meant to propagate observation context to a kotlin coroutine gRPC server method.
 *
 * Usage:
 *
 * ```
 * val server = ServerBuilder.forPort(8080)
 *         .intercept(ObservationCoroutineContextServerInterceptor(observationRegistry))
 *         .intercept(ObservationGrpcServerInterceptor(observationRegistry))
 *         .build();
 * server.start()
 * ```
 *
 * Please remember that order of interceptors matters, and it has to be the same as it is in the example above.
 *
 * @since 1.13.0
 */
class ObservationCoroutineContextServerInterceptor(
    private val observationRegistry: ObservationRegistry,
) : CoroutineContextServerInterceptor() {
    override fun coroutineContext(call: ServerCall<*, *>, headers: Metadata): CoroutineContext {
        return observationRegistry.asContextElement()
    }
}
