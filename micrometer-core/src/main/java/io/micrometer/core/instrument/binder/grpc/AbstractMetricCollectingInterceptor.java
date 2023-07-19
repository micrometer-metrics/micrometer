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

import io.grpc.MethodDescriptor;
import io.grpc.ServiceDescriptor;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import io.micrometer.core.instrument.binder.BaseUnits;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * An abstract gRPC interceptor that will collect metrics.
 *
 * @author Daniel Theuke (daniel.theuke@heuboe.de)
 * @since 1.7.0
 */
public abstract class AbstractMetricCollectingInterceptor {

    /**
     * The metrics tag key that belongs to the called service name.
     */
    private static final String TAG_SERVICE_NAME = "service";

    /**
     * The metrics tag key that belongs to the called method name.
     */
    private static final String TAG_METHOD_NAME = "method";

    /**
     * The metrics tag key that belongs to the type of the called method.
     */
    private static final String TAG_METHOD_TYPE = "methodType";

    /**
     * The metrics tag key that belongs to the result status code.
     */
    private static final String TAG_STATUS_CODE = "statusCode";

    /**
     * Creates a new counter builder for the given method. By default the base unit will
     * be messages.
     * @param method The method the counter will be created for.
     * @param name The name of the counter to use.
     * @param description The description of the counter to use.
     * @return The newly created counter builder.
     */
    protected static Counter.Builder prepareCounterFor(final MethodDescriptor<?, ?> method, final String name,
            final String description) {
        return Counter.builder(name)
            .description(description)
            .baseUnit(BaseUnits.MESSAGES)
            .tag(TAG_SERVICE_NAME, method.getServiceName())
            .tag(TAG_METHOD_NAME, method.getBareMethodName())
            .tag(TAG_METHOD_TYPE, method.getType().name());
    }

    /**
     * Creates a new timer builder for the given method.
     * @param method The method the timer will be created for.
     * @param name The name of the timer to use.
     * @param description The description of the timer to use.
     * @return The newly created timer builder.
     */
    protected static Timer.Builder prepareTimerFor(final MethodDescriptor<?, ?> method, final String name,
            final String description) {
        return Timer.builder(name)
            .description(description)
            .tag(TAG_SERVICE_NAME, method.getServiceName())
            .tag(TAG_METHOD_NAME, method.getBareMethodName())
            .tag(TAG_METHOD_TYPE, method.getType().name());
    }

    private final Map<MethodDescriptor<?, ?>, MetricSet> metricsForMethods = new ConcurrentHashMap<>();

    protected final MeterRegistry registry;

    protected final UnaryOperator<Counter.Builder> counterCustomizer;

    protected final UnaryOperator<Timer.Builder> timerCustomizer;

    protected final Status.Code[] eagerInitializedCodes;

    /**
     * Creates a new gRPC interceptor that will collect metrics into the given
     * {@link MeterRegistry}. This method won't use any customizers and will only
     * initialize the {@link Code#OK OK} status.
     * @param registry The registry to use.
     */
    protected AbstractMetricCollectingInterceptor(final MeterRegistry registry) {
        this(registry, UnaryOperator.identity(), UnaryOperator.identity(), Code.OK);
    }

    /**
     * Creates a new gRPC interceptor that will collect metrics into the given
     * {@link MeterRegistry} and uses the given customizers to configure the
     * {@link Counter}s and {@link Timer}s.
     * @param registry The registry to use.
     * @param counterCustomizer The unary function that can be used to customize the
     * created counters.
     * @param timerCustomizer The unary function that can be used to customize the created
     * timers.
     * @param eagerInitializedCodes The status codes that should be eager initialized.
     */
    protected AbstractMetricCollectingInterceptor(final MeterRegistry registry,
            final UnaryOperator<Counter.Builder> counterCustomizer, final UnaryOperator<Timer.Builder> timerCustomizer,
            final Status.Code... eagerInitializedCodes) {
        this.registry = registry;
        this.counterCustomizer = counterCustomizer;
        this.timerCustomizer = timerCustomizer;
        this.eagerInitializedCodes = eagerInitializedCodes;
    }

    /**
     * Pre-registers the all methods provided by the given service. This will initialize
     * all default counters and timers for those methods.
     * @param service The service to initialize the meters for.
     * @see #preregisterMethod(MethodDescriptor)
     */
    public void preregisterService(final ServiceDescriptor service) {
        for (final MethodDescriptor<?, ?> method : service.getMethods()) {
            preregisterMethod(method);
        }
    }

    /**
     * Pre-registers the given method. This will initialize all default counters and
     * timers for that method.
     * @param method The method to initialize the meters for.
     */
    public void preregisterMethod(final MethodDescriptor<?, ?> method) {
        metricsFor(method);
    }

    /**
     * Gets or creates a {@link MetricSet} for the given gRPC method. This will initialize
     * all default counters and timers for that method.
     * @param method The method to get the metric set for.
     * @return The metric set for the given method.
     * @see #newMetricsFor(MethodDescriptor)
     */
    protected final MetricSet metricsFor(final MethodDescriptor<?, ?> method) {
        return this.metricsForMethods.computeIfAbsent(method, this::newMetricsFor);
    }

    /**
     * Creates a {@link MetricSet} for the given gRPC method. This will initialize all
     * default counters and timers for that method.
     * @param method The method to get the metric set for.
     * @return The newly created metric set for the given method.
     */
    protected MetricSet newMetricsFor(final MethodDescriptor<?, ?> method) {
        return new MetricSet(newRequestCounterFor(method), newResponseCounterFor(method), newTimerFunction(method));
    }

    /**
     * Creates a new request counter for the given method.
     * @param method The method to create the counter for.
     * @return The newly created request counter.
     */
    protected abstract Counter newRequestCounterFor(final MethodDescriptor<?, ?> method);

    /**
     * Creates a new response counter for the given method.
     * @param method The method to create the counter for.
     * @return The newly created response counter.
     */
    protected abstract Counter newResponseCounterFor(final MethodDescriptor<?, ?> method);

    /**
     * Creates a new timer function using the given template. This method initializes the
     * default timers.
     * @param timerTemplate The template to create the instances from.
     * @return The newly created function that returns a timer for a given code.
     */
    protected Function<Code, Timer> asTimerFunction(final Supplier<Timer.Builder> timerTemplate) {
        final Map<Code, Timer> cache = new EnumMap<>(Code.class);
        final Function<Code, Timer> creator = code -> timerTemplate.get()
            .tag(TAG_STATUS_CODE, code.name())
            .register(this.registry);
        final Function<Code, Timer> cacheResolver = code -> cache.computeIfAbsent(code, creator);
        // Eager initialize
        for (final Code code : this.eagerInitializedCodes) {
            cacheResolver.apply(code);
        }
        return cacheResolver;
    }

    /**
     * Creates a new function that returns a timer for a given code for the given method.
     * @param method The method to create the timer for.
     * @return The newly created function that returns a timer for a given code.
     */
    protected abstract Function<Code, Timer> newTimerFunction(final MethodDescriptor<?, ?> method);

    /**
     * Container for all metrics of a certain call. Used instead of 3 maps to improve
     * performance.
     */
    protected static class MetricSet {

        private final Counter requestCounter;

        private final Counter responseCounter;

        private final Function<Code, Timer> timerFunction;

        /**
         * Creates a new metric set with the given meter instances.
         * @param requestCounter The request counter to use.
         * @param responseCounter The response counter to use.
         * @param timerFunction The timer function to use.
         */
        public MetricSet(final Counter requestCounter, final Counter responseCounter,
                final Function<Code, Timer> timerFunction) {

            this.requestCounter = requestCounter;
            this.responseCounter = responseCounter;
            this.timerFunction = timerFunction;
        }

        /**
         * Gets the Counter that counts the request messages.
         * @return The Counter that counts the request messages.
         */
        public Counter getRequestCounter() {
            return this.requestCounter;
        }

        /**
         * Gets the Counter that counts the response messages.
         * @return The Counter that counts the response messages.
         */
        public Counter getResponseCounter() {
            return this.responseCounter;
        }

        /**
         * Uses the given registry to create a {@link Sample Timer.Sample} that will be
         * reported if the returned consumer is invoked.
         * @param registry The registry used to create the sample.
         * @return The newly created consumer that will report the processing duration
         * since calling this method and invoking the returned consumer along with the
         * status code.
         */
        public Consumer<Status.Code> newProcessingDurationTiming(final MeterRegistry registry) {
            final Timer.Sample timerSample = Timer.start(registry);
            return code -> timerSample.stop(this.timerFunction.apply(code));
        }

    }

}
