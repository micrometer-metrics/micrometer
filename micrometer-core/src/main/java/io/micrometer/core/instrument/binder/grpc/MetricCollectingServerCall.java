/*
 * Copyright 2021 VMware, Inc.
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
import io.grpc.Status.Code;
import io.micrometer.core.instrument.Counter;

/**
 * A simple forwarding server call that collects metrics.
 *
 * @param <Q> The type of message received one or more times from the client.
 * @param <A> The type of message sent one or more times to the client.
 * @author Daniel Theuke (daniel.theuke@heuboe.de)
 */
class MetricCollectingServerCall<Q, A> extends SimpleForwardingServerCall<Q, A> {

    private final Counter responseCounter;

    private Code responseCode = Code.UNKNOWN;

    /**
     * Creates a new delegating ServerCall that will wrap the given server call to collect
     * metrics.
     * @param delegate The original call to wrap.
     * @param responseCounter The counter for outgoing responses.
     */
    public MetricCollectingServerCall(final ServerCall<Q, A> delegate, final Counter responseCounter) {

        super(delegate);
        this.responseCounter = responseCounter;
    }

    public Code getResponseCode() {
        return this.responseCode;
    }

    @Override
    public void close(final Status status, final Metadata responseHeaders) {
        this.responseCode = status.getCode();
        super.close(status, responseHeaders);
    }

    @Override
    public void sendMessage(final A responseMessage) {
        this.responseCounter.increment();
        super.sendMessage(responseMessage);
    }

}
