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

import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;
import io.micrometer.core.instrument.binder.grpc.GrpcObservationDocumentation.GrpcServerEvents;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Context;

/**
 * A simple forwarding server call for {@link Observation}.
 *
 * @param <ReqT> type of message sent one or more times to the server.
 * @param <RespT> type of message received one or more times from the server.
 */
class ObservationGrpcServerCall<ReqT, RespT> extends SimpleForwardingServerCall<ReqT, RespT> {

    private final Observation observation;

    ObservationGrpcServerCall(ServerCall<ReqT, RespT> delegate, Observation observation) {
        super(delegate);
        this.observation = observation;
    }

    @Override
    public void sendHeaders(Metadata headers) {
        super.sendHeaders(headers);
        Context context = this.observation.getContext();
        if (context instanceof GrpcServerObservationContext) {
            // Per javadoc, headers are not thread-safe. Make a copy.
            Metadata headersToKeep = new Metadata();
            headersToKeep.merge(headers);
            ((GrpcServerObservationContext) context).setHeaders(headersToKeep);
        }
    }

    @Override
    public void sendMessage(RespT message) {
        this.observation.event(GrpcServerEvents.MESSAGE_SENT);
        super.sendMessage(message);
    }

    @Override
    public void close(Status status, Metadata trailers) {
        if (status.getCause() != null) {
            this.observation.error(status.getCause());
        }
        // Per javadoc, trailers are not thread-safe. Make a copy.
        Metadata trailersToKeep = new Metadata();
        trailersToKeep.merge(trailers);
        GrpcServerObservationContext context = (GrpcServerObservationContext) this.observation.getContext();
        context.setStatusCode(status.getCode());
        context.setTrailers(trailersToKeep);
        context.setCancelled(isCancelled());
        super.close(status, trailers);
    }

}
