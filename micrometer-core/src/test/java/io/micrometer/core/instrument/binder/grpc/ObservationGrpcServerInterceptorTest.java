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

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.Marshaller;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Scope;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.ObservationTextPublisher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * @author Tadaya Tsuyukubo
 */
class ObservationGrpcServerInterceptorTest {

    @Test
    @SuppressWarnings("unchecked")
    void simulateAsyncClientRequest() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationTextPublisher());

        ServerCall<Object, Object> call = mock(ServerCall.class);
        Marshaller<Object> marshaller = mock(Marshaller.class);
        MethodDescriptor<Object, Object> methodDescriptor = MethodDescriptor.newBuilder(marshaller, marshaller)
            .setType(MethodType.UNARY)
            .setFullMethodName("greeter/hello")
            .build();
        given(call.getMethodDescriptor()).willReturn(methodDescriptor);

        Metadata metadata = new Metadata();

        ServerCallHandler<Object, Object> handler = mock(ServerCallHandler.class);
        Listener<Object> mockListener = mock(Listener.class);
        given(handler.startCall(any(), any())).willReturn(mockListener);

        ObservationGrpcServerInterceptor interceptor = new ObservationGrpcServerInterceptor(registry);

        // For async request, gprc first calls "interceptCall" multiple times
        Listener<Object> listenerA = interceptor.interceptCall(call, metadata, handler);
        Observation observationA = registry.getCurrentObservation();
        Scope scopeA = registry.getCurrentObservationScope();

        Listener<Object> listenerB = interceptor.interceptCall(call, metadata, handler);
        Observation observationB = registry.getCurrentObservation();
        Scope scopeB = registry.getCurrentObservationScope();

        Listener<Object> listenerC = interceptor.interceptCall(call, metadata, handler);
        Observation observationC = registry.getCurrentObservation();
        Scope scopeC = registry.getCurrentObservationScope();

        assertThat(observationA).isNotEqualTo(observationB).isNotEqualTo(observationC);
        assertThat(observationB).isNotEqualTo(observationC);

        // then, call the listener methods. validate the current scope and observation
        listenerA.onMessage(new Object());
        assertThat(listenerA).isInstanceOf(ObservationGrpcServerCallListener.class);
        assertThat(registry.getCurrentObservation()).isSameAs(observationA);
        assertThat(registry.getCurrentObservationScope()).isSameAs(scopeA);

        listenerB.onMessage(new Object());
        assertThat(registry.getCurrentObservation()).isSameAs(observationB);
        assertThat(registry.getCurrentObservationScope()).isSameAs(scopeB);

        listenerC.onMessage(new Object());
        assertThat(registry.getCurrentObservation()).isSameAs(observationC);
        assertThat(registry.getCurrentObservationScope()).isSameAs(scopeC);

        listenerA.onHalfClose();
        assertThat(registry.getCurrentObservation()).isSameAs(observationA);
        assertThat(registry.getCurrentObservationScope()).isSameAs(scopeA);
    }

}
