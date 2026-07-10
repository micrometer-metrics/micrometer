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
 * A gRPC server interceptor that will collect metrics using the given
 * {@link MeterRegistry}.
 *
 * <p>
 * <b>Usage:</b>
 * </p>
 *
 * <pre>
 * Server server = ServerBuilder.forPort(8080)
 *         .intercept(new MetricCollectingServerInterceptor(meterRegistry))
 *         .build();
 *
 * server.start()
 * </pre>
 *
 * @author Daniel Theuke (daniel.theuke@heuboe.de)
 * @since 1.7.0
 */
public class MetricCollectingServerInterceptor extends AbstractMetricCollectingInterceptor
        implements ServerInterceptor {

    /**
     * The total number of requests received
     */
    private static final String METRIC_NAME_SERVER_REQUESTS_RECEIVED = "grpc.server.requests.received";

    /**
     * The total number of responses sent
     */
    private static final String METRIC_NAME_SERVER_RESPONSES_SENT = "grpc.server.responses.sent";

    /**
     * The total time taken for the server to complete the call.
     */
    private static final String METRIC_NAME_SERVER_PROCESSING_DURATION = "grpc.server.processing.duration";

    /**
     * Creates a new gRPC server interceptor that will collect metrics into the given
     * {@link MeterRegistry}.
     * @param registry The registry to use.
     */
    public MetricCollectingServerInterceptor(final MeterRegistry registry) {
        super(registry);
    }

    /**
     * Creates a new gRPC server interceptor that will collect metrics into the given
     * {@link MeterRegistry} and uses the given customizers to configure the
     * {@link Counter}s and {@link Timer}s.
     * @param registry The registry to use.
     * @param counterCustomizer The unary function that can be used to customize the
     * created counters.
     * @param timerCustomizer The unary function that can be used to customize the created
     * timers.
     * @param eagerInitializedCodes The status codes that should be eager initialized.
     */
    public MetricCollectingServerInterceptor(final MeterRegistry registry,
            final UnaryOperator<Counter.Builder> counterCustomizer, final UnaryOperator<Timer.Builder> timerCustomizer,
            final Code... eagerInitializedCodes) {
        super(registry, counterCustomizer, timerCustomizer, eagerInitializedCodes);
    }

    /**
     * Pre-registers the all methods provided by the given service. This will initialize
     * all default counters and timers for those methods.
     * @param service The service to initialize the meters for.
     * @see #preregisterService(ServerServiceDefinition)
     */
    public void preregisterService(final BindableService service) {
        preregisterService(service.bindService());
    }

    /**
     * Pre-registers the all methods provided by the given service. This will initialize
     * all default counters and timers for those methods.
     * @param serviceDefinition The service to initialize the meters for.
     * @see #preregisterService(ServiceDescriptor)
     */
    public void preregisterService(final ServerServiceDefinition serviceDefinition) {
        preregisterService(serviceDefinition.getServiceDescriptor());
    }

    @Override
    protected Counter newRequestCounterFor(final MethodDescriptor<?, ?> method) {
        return this.counterCustomizer
            .apply(prepareCounterFor(method, METRIC_NAME_SERVER_REQUESTS_RECEIVED,
                    "The total number of requests received"))
            .register(this.registry);
    }

    @Override
    protected Counter newResponseCounterFor(final MethodDescriptor<?, ?> method) {
        return this.counterCustomizer
            .apply(prepareCounterFor(method, METRIC_NAME_SERVER_RESPONSES_SENT, "The total number of responses sent"))
            .register(this.registry);
    }

    @Override
    protected Function<Code, Timer> newTimerFunction(final MethodDescriptor<?, ?> method) {
        return asTimerFunction(() -> this.timerCustomizer.apply(prepareTimerFor(method,
                METRIC_NAME_SERVER_PROCESSING_DURATION, "The total time taken for the server to complete the call")));
    }

    @Override
    public <Q, A> ServerCall.Listener<Q> interceptCall(final ServerCall<Q, A> call, final Metadata requestHeaders,
            final ServerCallHandler<Q, A> next) {

        final MetricSet metrics = metricsFor(call.getMethodDescriptor());
        final Consumer<Status.Code> responseStatusTiming = metrics.newProcessingDurationTiming(this.registry);

        final MetricCollectingServerCall<Q, A> monitoringCall = new MetricCollectingServerCall<>(call,
                metrics.getResponseCounter());

        return new MetricCollectingServerCallListener<>(next.startCall(monitoringCall, requestHeaders),
                metrics.getRequestCounter(), monitoringCall::getResponseCode, responseStatusTiming);
    }

}
