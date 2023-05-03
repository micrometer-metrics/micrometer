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

import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.ServerCall.Listener;
import io.micrometer.core.instrument.binder.grpc.GrpcObservationDocumentation.GrpcServerEvents;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Scope;
import io.micrometer.observation.ObservationRegistry;

/**
 * A simple forwarding client call listener for {@link Observation}.
 *
 * @param <RespT> type of message received one or more times from the server.
 */
class ObservationGrpcServerCallListener<RespT> extends SimpleForwardingServerCallListener<RespT> {

    private final Scope scope;

    private final ObservationRegistry registry;

    ObservationGrpcServerCallListener(Listener<RespT> delegate, Scope scope, ObservationRegistry registry) {
        super(delegate);
        this.scope = scope;
        this.registry = registry;
    }

    @Override
    public void onMessage(RespT message) {
        if (this.scope != this.registry.getCurrentObservationScope()) {
            this.scope.makeCurrent();
            this.registry.setCurrentObservationScope(this.scope);
        }
        this.scope.getCurrentObservation().event(GrpcServerEvents.MESSAGE_RECEIVED);
        super.onMessage(message);
    }

    @Override
    public void onHalfClose() {
        if (this.scope != this.registry.getCurrentObservationScope()) {
            this.scope.makeCurrent();
            this.registry.setCurrentObservationScope(this.scope);
        }
        try {
            super.onHalfClose();
        }
        catch (Throwable ex) {
            handleFailure(ex);
            throw ex;
        }
    }

    private void handleFailure(Throwable ex) {
        Observation observation = this.scope.getCurrentObservation();
        this.scope.close();
        observation.error(ex).stop();
    }

}
