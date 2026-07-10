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

import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.Status;
import io.micrometer.core.instrument.Counter;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A simple forwarding server call listener that collects metrics.
 *
 * @param <Q> The type of message received one or more times from the client.
 * @author Daniel Theuke (daniel.theuke@heuboe.de)
 */
class MetricCollectingServerCallListener<Q> extends SimpleForwardingServerCallListener<Q> {

    private final Counter requestCounter;

    private final Supplier<Status.Code> responseCodeSupplier;

    private final Consumer<Status.Code> responseStatusTiming;

    /**
     * Creates a new delegating {@link ServerCall.Listener} that will wrap the given
     * server call listener to collect metrics.
     * @param delegate The original listener to wrap.
     * @param requestCounter The counter for incoming requests.
     * @param responseCodeSupplier The supplier of the response code.
     * @param responseStatusTiming The consumer used to time the processing duration along
     * with a response status.
     */

    public MetricCollectingServerCallListener(final Listener<Q> delegate, final Counter requestCounter,
            final Supplier<Status.Code> responseCodeSupplier, final Consumer<Status.Code> responseStatusTiming) {

        super(delegate);
        this.requestCounter = requestCounter;
        this.responseCodeSupplier = responseCodeSupplier;
        this.responseStatusTiming = responseStatusTiming;
    }

    @Override
    public void onMessage(final Q requestMessage) {
        this.requestCounter.increment();
        super.onMessage(requestMessage);
    }

    @Override
    public void onComplete() {
        report(this.responseCodeSupplier.get());
        super.onComplete();
    }

    @Override
    public void onCancel() {
        report(Status.Code.CANCELLED);
        super.onCancel();
    }

    private void report(final Status.Code code) {
        this.responseStatusTiming.accept(code);
    }

}
