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
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.Status;
import io.micrometer.core.instrument.binder.grpc.GrpcObservationDocumentation.GrpcClientEvents;
import io.micrometer.observation.Observation;

/**
 * A simple forwarding client call listener for {@link Observation}.
 *
 * @param <RespT> type of message received one or more times from the server.
 */
class ObservationGrpcClientCallListener<RespT> extends SimpleForwardingClientCallListener<RespT> {

    private final Observation observation;

    ObservationGrpcClientCallListener(ClientCall.Listener<RespT> delegate, Observation observation) {
        super(delegate);
        this.observation = observation;
    }

    @Override
    public void onHeaders(Metadata headers) {
        super.onHeaders(headers);
        // Per javadoc, headers are not thread-safe. Make a copy.
        Metadata headersToKeep = new Metadata();
        headersToKeep.merge(headers);
        GrpcClientObservationContext context = (GrpcClientObservationContext) this.observation.getContext();
        context.setHeaders(headersToKeep);
    }

    @Override
    public void onClose(Status status, Metadata trailers) {
        GrpcClientObservationContext context = (GrpcClientObservationContext) this.observation.getContext();
        context.setStatusCode(status.getCode());
        context.setTrailers(trailers);
        if (status.getCause() != null) {
            observation.error(status.getCause());
        }
        this.observation.stop();
        // We do not catch exception from the delegate. (following Brave design)
        super.onClose(status, trailers);
    }

    @Override
    public void onMessage(RespT message) {
        this.observation.event(GrpcClientEvents.MESSAGE_RECEIVED);
        super.onMessage(message);
    }

}
