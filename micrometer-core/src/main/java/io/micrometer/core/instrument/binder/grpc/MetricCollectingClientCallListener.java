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

import io.grpc.ClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.Status;
import io.micrometer.core.instrument.Counter;

import java.util.function.Consumer;

/**
 * A simple forwarding client call listener that collects metrics.
 *
 * @param <A> The type of message received one or more times from the server.
 * @author Daniel Theuke (daniel.theuke@heuboe.de)
 */
class MetricCollectingClientCallListener<A> extends SimpleForwardingClientCallListener<A> {

    private final Counter responseCounter;

    private final Consumer<Status.Code> processingDurationTiming;

    /**
     * Creates a new delegating {@link ClientCall.Listener} that will wrap the given
     * client call listener to collect metrics.
     * @param delegate The original call to wrap.
     * @param responseCounter The counter for incoming responses.
     * @param processingDurationTiming The consumer used to time the processing duration
     * along with a response status.
     */
    public MetricCollectingClientCallListener(final ClientCall.Listener<A> delegate, final Counter responseCounter,
            final Consumer<Status.Code> processingDurationTiming) {

        super(delegate);
        this.responseCounter = responseCounter;
        this.processingDurationTiming = processingDurationTiming;
    }

    @Override
    public void onClose(final Status status, final Metadata metadata) {
        this.processingDurationTiming.accept(status.getCode());
        super.onClose(status, metadata);
    }

    @Override
    public void onMessage(final A responseMessage) {
        this.responseCounter.increment();
        super.onMessage(responseMessage);
    }

}
