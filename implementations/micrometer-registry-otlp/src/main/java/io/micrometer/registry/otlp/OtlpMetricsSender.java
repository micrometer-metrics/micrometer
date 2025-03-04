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
package io.micrometer.registry.otlp;

import java.util.Map;

/**
 * This is responsible for sending OTLP format metrics to a compatible location. Specific
 * implementations can use different transports or clients for sending the metrics.
 *
 * @since 1.15.0
 */
public interface OtlpMetricsSender extends MetricsSender {

    /**
     * Send a batch of OTLP Protobuf format metrics to an OTLP receiver.
     * @param address address of the OTLP receiver to which metrics will be sent
     * @param metricsData OTLP protobuf encoded batch of metrics
     * @param headers metadata to send as headers with the metrics data
     * @throws Throwable when there is an exception in sending the metrics; the caller
     * should handle this in some way such as logging the exception
     */
    @Override
    void send(String address, byte[] metricsData, Map<String, String> headers) throws Throwable;

}
