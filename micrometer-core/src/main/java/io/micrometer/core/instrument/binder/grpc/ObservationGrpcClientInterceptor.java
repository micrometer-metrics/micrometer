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

import io.grpc.*;
import io.grpc.Metadata.Key;
import io.grpc.MethodDescriptor.MethodType;
import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A gRPC client interceptor that works with {@link Observation}.
 * <p>
 * <b>Usage:</b>
 * </p>
 * <pre>
 * ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8080)
 *     .intercept(new ObservationGrpcClientInterceptor(observationRegistry))
 *     .build();
 * channel.newCall(method, options);
 * </pre> The instrumentation is based on the behavior of Spring Cloud Sleuth and Brave.
 *
 * @author Tadaya Tsuyukubo
 * @since 1.10.0
 */
public class ObservationGrpcClientInterceptor implements ClientInterceptor {

    private static final GrpcClientObservationConvention DEFAULT_CONVENTION = new DefaultGrpcClientObservationConvention();

    private static final Map<String, Key<String>> KEY_CACHE = new ConcurrentHashMap<>();

    private final ObservationRegistry registry;

    @Nullable
    private GrpcClientObservationConvention customConvention;

    public ObservationGrpcClientInterceptor(ObservationRegistry registry) {
        this.registry = registry;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions, Channel next) {
        Supplier<GrpcClientObservationContext> contextSupplier = () -> {
            GrpcClientObservationContext context = new GrpcClientObservationContext((carrier, keyName, value) -> {
                Key<String> key = KEY_CACHE.computeIfAbsent(keyName,
                        (k) -> Key.of(keyName, Metadata.ASCII_STRING_MARSHALLER));
                carrier.removeAll(key);
                carrier.put(key, value);
            });

            String serviceName = method.getServiceName();
            String methodName = method.getBareMethodName();
            String fullMethodName = method.getFullMethodName();
            MethodType methodType = method.getType();
            if (serviceName != null) {
                context.setServiceName(serviceName);
            }
            if (methodName != null) {
                context.setMethodName(methodName);
            }
            context.setFullMethodName(fullMethodName);
            context.setMethodType(methodType);
            String authority = next.authority();
            context.setAuthority(authority);
            try {
                URI uri = new URI(null, authority, null, null, null);
                context.setPeerName(uri.getHost());
                context.setPeerPort(uri.getPort());
            }
            catch (Exception ex) {
            }
            return context;
        };

        Observation observation = GrpcObservationDocumentation.CLIENT.observation(this.customConvention,
                DEFAULT_CONVENTION, contextSupplier, this.registry);

        if (observation.isNoop()) {
            // do not instrument anymore
            return next.newCall(method, callOptions);
        }
        return new ObservationGrpcClientCall<>(next.newCall(method, callOptions), observation);
    }

    /**
     * Set a custom {@link GrpcClientObservationConvention}.
     * @param customConvention a custom convention
     */
    public void setCustomConvention(@Nullable GrpcClientObservationConvention customConvention) {
        this.customConvention = customConvention;
    }

}
