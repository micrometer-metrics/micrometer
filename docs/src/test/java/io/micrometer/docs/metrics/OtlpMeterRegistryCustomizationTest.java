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

import io.micrometer.common.lang.NonNullApi;
import io.micrometer.core.ipc.http.OkHttpSender;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpHttpMetricsSender;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.micrometer.registry.otlp.OtlpMetricsSender;
import org.junit.jupiter.api.Test;

import java.util.Map;

class OtlpMeterRegistryCustomizationTest {

    @Test
    void customizeHttpSender() {
        // tag::customizeHttpSender[]
        OtlpConfig config = OtlpConfig.DEFAULT;
        OtlpHttpMetricsSender httpMetricsSender = new OtlpHttpMetricsSender(new OkHttpSender(), config);
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

    @NonNullApi
    private static class OtlpGrpcMetricsSender implements OtlpMetricsSender {

        @Override
        public void send(byte[] metricsData, Map<String, String> headers) {
        }

    }

}
