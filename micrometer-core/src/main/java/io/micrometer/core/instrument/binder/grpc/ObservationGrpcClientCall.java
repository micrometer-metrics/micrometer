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

import io.grpc.ClientCall;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.Metadata;
import io.micrometer.core.instrument.binder.grpc.GrpcObservationDocumentation.GrpcClientEvents;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Scope;

/**
 * A simple forwarding client call for {@link Observation}.
 *
 * @param <ReqT> type of message sent one or more times to the server.
 * @param <RespT> type of message received one or more times from the server.
 */
class ObservationGrpcClientCall<ReqT, RespT> extends SimpleForwardingClientCall<ReqT, RespT> {

    private final Observation observation;

    ObservationGrpcClientCall(ClientCall<ReqT, RespT> delegate, Observation observation) {
        super(delegate);
        this.observation = observation;
    }

    @Override
    public void start(Listener<RespT> responseListener, Metadata metadata) {
        ((GrpcClientObservationContext) this.observation.getContext()).setCarrier(metadata);
        try (Scope scope = this.observation.start().openScope()) {
            super.start(new ObservationGrpcClientCallListener<>(responseListener, this.observation), metadata);
        }
        catch (Throwable ex) {
            handleFailure(ex);
            throw ex;
        }
    }

    @Override
    public void halfClose() {
        try (Scope scope = this.observation.openScope()) {
            super.halfClose();
        }
        catch (Throwable ex) {
            handleFailure(ex);
            throw ex;
        }
    }

    @Override
    public void sendMessage(ReqT message) {
        this.observation.event(GrpcClientEvents.MESSAGE_SENT);
        try (Scope scope = this.observation.openScope()) {
            super.sendMessage(message);
        }
    }

    private void handleFailure(Throwable ex) {
        this.observation.error(ex).stop();
    }

}
