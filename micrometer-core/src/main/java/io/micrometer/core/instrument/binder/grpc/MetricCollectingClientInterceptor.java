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

import io.grpc.*;
import io.grpc.Status.Code;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * A gRPC client interceptor that will collect metrics using the given
 * {@link MeterRegistry}.
 *
 * <p>
 * <b>Usage:</b>
 * </p>
 *
 * <pre>
 * ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8080)
 *     .intercept(new MetricCollectingClientInterceptor(meterRegistry))
 *     .build();
 *
 * channel.newCall(method, options);
 * </pre>
 *
 * @author Daniel Theuke (daniel.theuke@heuboe.de)
 * @since 1.7.0
 */
public class MetricCollectingClientInterceptor extends AbstractMetricCollectingInterceptor
        implements ClientInterceptor {

    /**
     * The total number of requests sent
     */
    private static final String METRIC_NAME_CLIENT_REQUESTS_SENT = "grpc.client.requests.sent";

    /**
     * The total number of responses received
     */
    private static final String METRIC_NAME_CLIENT_RESPONSES_RECEIVED = "grpc.client.responses.received";

    /**
     * The total time taken for the client to complete the call, including network delay
     */
    private static final String METRIC_NAME_CLIENT_PROCESSING_DURATION = "grpc.client.processing.duration";

    /**
     * Creates a new gRPC client interceptor that will collect metrics into the given
     * {@link MeterRegistry}.
     * @param registry The registry to use.
     */
    public MetricCollectingClientInterceptor(final MeterRegistry registry) {
        super(registry);
    }

    /**
     * Creates a new gRPC client interceptor that will collect metrics into the given
     * {@link MeterRegistry} and uses the given customizers to configure the
     * {@link Counter}s and {@link Timer}s.
     * @param registry The registry to use.
     * @param counterCustomizer The unary function that can be used to customize the
     * created counters.
     * @param timerCustomizer The unary function that can be used to customize the created
     * timers.
     * @param eagerInitializedCodes The status codes that should be eager initialized.
     */
    public MetricCollectingClientInterceptor(final MeterRegistry registry,
            final UnaryOperator<Counter.Builder> counterCustomizer, final UnaryOperator<Timer.Builder> timerCustomizer,
            final Code... eagerInitializedCodes) {
        super(registry, counterCustomizer, timerCustomizer, eagerInitializedCodes);
    }

    @Override
    protected Counter newRequestCounterFor(final MethodDescriptor<?, ?> method) {
        return this.counterCustomizer
            .apply(prepareCounterFor(method, METRIC_NAME_CLIENT_REQUESTS_SENT, "The total number of requests sent"))
            .register(this.registry);
    }

    @Override
    protected Counter newResponseCounterFor(final MethodDescriptor<?, ?> method) {
        return this.counterCustomizer
            .apply(prepareCounterFor(method, METRIC_NAME_CLIENT_RESPONSES_RECEIVED,
                    "The total number of responses received"))
            .register(this.registry);
    }

    @Override
    protected Function<Code, Timer> newTimerFunction(final MethodDescriptor<?, ?> method) {
        return asTimerFunction(
                () -> this.timerCustomizer.apply(prepareTimerFor(method, METRIC_NAME_CLIENT_PROCESSING_DURATION,
                        "The total time taken for the client to complete the call, including network delay")));
    }

    @Override
    public <Q, A> ClientCall<Q, A> interceptCall(final MethodDescriptor<Q, A> methodDescriptor,
            final CallOptions callOptions, final Channel channel) {

        final MetricSet metrics = metricsFor(methodDescriptor);
        final Consumer<Code> processingDurationTiming = metrics.newProcessingDurationTiming(this.registry);

        return new MetricCollectingClientCall<>(channel.newCall(methodDescriptor, callOptions),
                metrics.getRequestCounter(), metrics.getResponseCounter(), processingDurationTiming);
    }

}
