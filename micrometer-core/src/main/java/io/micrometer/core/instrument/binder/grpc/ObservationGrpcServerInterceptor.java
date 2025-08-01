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
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.ServerCall.Listener;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.Propagator;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A gRPC server interceptor that works with {@link Observation}.
 * <p>
 * <b>Usage:</b>
 * </p>
 * <pre>
 * Server server = ServerBuilder.forPort(8080)
 *         .intercept(new ObservationGrpcServerInterceptor(observationRegistry))
 *         .build();
 * server.start()
 * </pre> The instrumentation is based on the behavior of Spring Cloud Sleuth and Brave.
 *
 * @author Tadaya Tsuyukubo
 * @since 1.10.0
 */
public class ObservationGrpcServerInterceptor implements ServerInterceptor {

    private static final GrpcServerObservationConvention DEFAULT_CONVENTION = new DefaultGrpcServerObservationConvention();

    private static final Map<String, Metadata.Key<String>> KEY_CACHE = new ConcurrentHashMap<>();

    private final ObservationRegistry registry;

    private @Nullable GrpcServerObservationConvention customConvention;

    public ObservationGrpcServerInterceptor(ObservationRegistry registry) {
        this.registry = registry;
    }

    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        Supplier<GrpcServerObservationContext> contextSupplier = () -> {
            GrpcServerObservationContext context = new GrpcServerObservationContext(new Propagator.Getter<Metadata>() {
                @Override
                public @Nullable String get(Metadata carrier, String keyName) {
                    return carrier.get(getKey(keyName));
                }

                private Metadata.Key<String> getKey(String keyName) {
                    return KEY_CACHE.computeIfAbsent(keyName,
                            (k) -> Metadata.Key.of(keyName, Metadata.ASCII_STRING_MARSHALLER));
                }

                @Override
                public Iterable<String> getAll(Metadata carrier, String keyName) {
                    Iterable<String> all = carrier.getAll(getKey(keyName));
                    return all == null ? Collections.emptyList() : all;
                }
            });
            context.setCarrier(headers);

            MethodDescriptor<ReqT, RespT> methodDescriptor = call.getMethodDescriptor();
            String serviceName = methodDescriptor.getServiceName();
            String methodName = methodDescriptor.getBareMethodName();
            String fullMethodName = methodDescriptor.getFullMethodName();
            MethodType methodType = methodDescriptor.getType();
            if (serviceName != null) {
                context.setServiceName(serviceName);
            }
            if (methodName != null) {
                context.setMethodName(methodName);
            }
            context.setFullMethodName(fullMethodName);
            context.setMethodType(methodType);
            String authority = call.getAuthority();
            if (authority != null) {
                context.setAuthority(authority);
                try {
                    URI uri = new URI(null, authority, null, null, null);
                    context.setPeerName(uri.getHost());
                    context.setPeerPort(uri.getPort());
                }
                catch (Exception ex) {
                }
            }
            return context;
        };

        Observation observation = GrpcObservationDocumentation.SERVER
            .observation(this.customConvention, DEFAULT_CONVENTION, contextSupplier, this.registry)
            .start();

        if (observation.isNoop()) {
            // do not instrument anymore
            return next.startCall(call, headers);
        }

        ObservationGrpcServerCall<ReqT, RespT> serverCall = new ObservationGrpcServerCall<>(call, observation);

        try (Observation.Scope scope = observation.openScope()) {
            Listener<ReqT> result = next.startCall(serverCall, headers);
            return new ObservationGrpcServerCallListener<>(result, observation);
        }
        catch (Exception ex) {
            observation.error(ex).stop();
            throw ex;
        }
    }

    /**
     * Set a custom {@link GrpcServerObservationConvention}.
     * @param customConvention a custom convention
     */
    public void setCustomConvention(@Nullable GrpcServerObservationConvention customConvention) {
        this.customConvention = customConvention;
    }

}
