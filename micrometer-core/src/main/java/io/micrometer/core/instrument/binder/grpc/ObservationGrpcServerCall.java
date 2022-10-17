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
import io.micrometer.observation.Observation.Scope;

/**
 * A simple forwarding server call for {@link Observation}.
 *
 * @param <ReqT> type of message sent one or more times to the server.
 * @param <RespT> type of message received one or more times from the server.
 */
class ObservationGrpcServerCall<ReqT, RespT> extends SimpleForwardingServerCall<ReqT, RespT> {

    private final Scope scope;

    ObservationGrpcServerCall(ServerCall<ReqT, RespT> delegate, Scope scope) {
        super(delegate);
        this.scope = scope;
    }

    @Override
    public void sendMessage(RespT message) {
        this.scope.getCurrentObservation().event(GrpcServerEvents.MESSAGE_SENT);
        super.sendMessage(message);
    }

    @Override
    public void close(Status status, Metadata trailers) {
        Observation observation = this.scope.getCurrentObservation();

        if (status.getCause() != null) {
            observation.error(status.getCause());
        }

        GrpcServerObservationContext context = (GrpcServerObservationContext) observation.getContext();
        context.setStatusCode(status.getCode());

        this.scope.close();
        observation.stop();

        super.close(status, trailers);
    }

}
