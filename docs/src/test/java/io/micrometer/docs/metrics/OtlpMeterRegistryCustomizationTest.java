/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.docs.metrics;

import io.micrometer.core.ipc.http.OkHttpSender;
import io.micrometer.registry.otlp.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class OtlpMeterRegistryCustomizationTest {

    @Test
    void customizeHttpSender() {
        // tag::customizeHttpSender[]
        OtlpConfig config = OtlpConfig.DEFAULT;
        OtlpHttpMetricsSender httpMetricsSender = new OtlpHttpMetricsSender(new OkHttpSender());
        OtlpMeterRegistry meterRegistry = OtlpMeterRegistry.builder(config).metricsSender(httpMetricsSender).build();
        // end::customizeHttpSender[]
    }

    @Test
    void customizeOtlpSender() {
        // tag::customGrpcSender[]
        OtlpConfig config = OtlpConfig.DEFAULT;
        OtlpMetricsSender metricsSender = new OtlpGrpcMetricsSender();
        OtlpMeterRegistry meterRegistry = OtlpMeterRegistry.builder(config).metricsSender(metricsSender).build();
        // end::customGrpcSender[]
    }

    @Test
    void customizeExemplarContextProvider() {
        // tag::customizeExemplarContextProvider[]
        OtlpConfig config = OtlpConfig.DEFAULT;
        ExemplarContextProvider contextProvider = new TracingExemplarContextProvider();
        OtlpMeterRegistry meterRegistry = OtlpMeterRegistry.builder(config)
            .exemplarContextProvider(contextProvider)
            .build();
        // end::customizeExemplarContextProvider[]
    }

    private static class OtlpGrpcMetricsSender implements OtlpMetricsSender {

        @Override
        public void send(@NonNull Request request) {
        }

    }

    private static class TracingExemplarContextProvider implements ExemplarContextProvider {

        @Override
        public @Nullable OtlpExemplarContext getExemplarContext() {
            return null;
        }

    }

}
