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
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.Status;
import io.micrometer.core.instrument.Counter;

import java.util.function.Consumer;

/**
 * A simple forwarding client call that collects metrics.
 *
 * @param <Q> The type of message sent one or more times to the server.
 * @param <A> The type of message received one or more times from the server.
 * @author Daniel Theuke (daniel.theuke@heuboe.de)
 */
class MetricCollectingClientCall<Q, A> extends SimpleForwardingClientCall<Q, A> {

    private final Counter requestCounter;

    private final Counter responseCounter;

    private final Consumer<Status.Code> processingDurationTiming;

    /**
     * Creates a new delegating ClientCall that will wrap the given client call to collect
     * metrics.
     * @param delegate The original call to wrap.
     * @param requestCounter The counter for outgoing requests.
     * @param responseCounter The counter for incoming responses.
     * @param processingDurationTiming The consumer used to time the processing duration
     * along with a response status.
     */
    MetricCollectingClientCall(final ClientCall<Q, A> delegate, final Counter requestCounter,
            final Counter responseCounter, final Consumer<Status.Code> processingDurationTiming) {

        super(delegate);
        this.requestCounter = requestCounter;
        this.responseCounter = responseCounter;
        this.processingDurationTiming = processingDurationTiming;
    }

    @Override
    public void start(final ClientCall.Listener<A> responseListener, final Metadata metadata) {
        super.start(new MetricCollectingClientCallListener<>(responseListener, this.responseCounter,
                this.processingDurationTiming), metadata);
    }

    @Override
    public void sendMessage(final Q requestMessage) {
        this.requestCounter.increment();
        super.sendMessage(requestMessage);
    }

}
