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

/**
 * A simple forwarding client call listener for {@link Observation}.
 *
 * @param <RespT> type of message received one or more times from the server.
 */
class ObservationGrpcServerCallListener<RespT> extends SimpleForwardingServerCallListener<RespT> {

    private final Scope scope;

    ObservationGrpcServerCallListener(Listener<RespT> delegate, Scope scope) {
        super(delegate);
        this.scope = scope;
    }

    @Override
    public void onMessage(RespT message) {
        this.scope.getCurrentObservation().event(GrpcServerEvents.MESSAGE_RECEIVED);
        super.onMessage(message);
    }

    @Override
    public void onHalfClose() {
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
