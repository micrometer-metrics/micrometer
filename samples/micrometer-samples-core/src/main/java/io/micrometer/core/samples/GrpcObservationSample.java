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
package io.micrometer.core.samples;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.health.v1.HealthGrpc.HealthBlockingStub;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.protobuf.services.HealthStatusManager;
import io.micrometer.core.instrument.binder.grpc.ObservationGrpcClientInterceptor;
import io.micrometer.core.instrument.binder.grpc.ObservationGrpcServerInterceptor;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.ObservationTextPublisher;

import java.io.IOException;

/**
 * Demonstrates how to use observation gRPC interceptors. To see more details, check the
 * {@code GrpcObservationTest} in micrometer-core test.
 *
 * @author Tadaya Tsuyukubo
 */
public class GrpcObservationSample {

    public static void main(String[] args) throws IOException {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig()
            .observationHandler(new ObservationTextPublisher())
            .observationHandler(new DefaultMeterObservationHandler(meterRegistry));

        HealthStatusManager service = new HealthStatusManager();

        Server server = InProcessServerBuilder.forName("sample")
            .addService(service.getHealthService())
            .intercept(new ObservationGrpcServerInterceptor(observationRegistry))
            .build();
        server.start();

        ManagedChannel channel = InProcessChannelBuilder.forName("sample")
            .intercept(new ObservationGrpcClientInterceptor(observationRegistry))
            .build();

        HealthBlockingStub healthClient = HealthGrpc.newBlockingStub(channel);

        HealthCheckRequest request = HealthCheckRequest.getDefaultInstance();
        HealthCheckResponse response = healthClient.check(request);

        System.out.println("Check Status: " + response.getStatus());
        System.out.println(meterRegistry.getMetersAsString());

        channel.shutdownNow();
        server.shutdownNow();
    }

}
