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
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.grpc.MetricCollectingClientInterceptor;
import io.micrometer.core.instrument.binder.grpc.MetricCollectingServerInterceptor;
import io.micrometer.core.samples.utils.SampleConfig;

import java.io.IOException;

/**
 * Demonstrates how to collect metrics for grpc-java clients and servers.
 *
 * @author Daniel Theuke (daniel.theuke@heuboe.de)
 */
public class GrpcMetricsSample {

    public static void main(final String... args) throws IOException {
        final MeterRegistry registry = SampleConfig.myMonitoringSystem();

        final HealthStatusManager service = new HealthStatusManager();

        final Server server = InProcessServerBuilder.forName("sample")
            .addService(service.getHealthService()) // Or
                                                    // any
                                                    // other
                                                    // service(s)
            .intercept(new MetricCollectingServerInterceptor(registry))
            .build();

        server.start();

        final ManagedChannel channel = InProcessChannelBuilder.forName("sample")
            .intercept(new MetricCollectingClientInterceptor(registry))
            .build();

        final HealthBlockingStub healthClient = HealthGrpc.newBlockingStub(channel); // Or
                                                                                     // any
                                                                                     // other
                                                                                     // stub(s)

        final HealthCheckRequest request = HealthCheckRequest.getDefaultInstance();
        final HealthCheckResponse response = healthClient.check(request);

        System.out.println("Status: " + response.getStatus());

        for (final Meter meter : registry.getMeters()) {
            System.out.println(meter.getClass().getSimpleName() + "->" + meter.getId());
        }

        channel.shutdownNow();
        server.shutdownNow();
    }

}
