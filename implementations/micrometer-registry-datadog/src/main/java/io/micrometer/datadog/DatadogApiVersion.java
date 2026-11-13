/*
 * Copyright 2026 VMware, Inc.
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
package io.micrometer.datadog;

/**
 * Version of the Datadog API that the {@link DatadogMeterRegistry} sends the series of
 * metrics to.
 *
 * @author Sharang Gupta
 * @since 1.18.0
 * @see DatadogConfig#apiVersion()
 */
public enum DatadogApiVersion {

    /**
     * The v1 API, which receives the series at {@code /api/v1/series}.
     */
    V1("/api/v1/series"),

    /**
     * The v2 API, which receives the series at {@code /api/v2/series}. Datadog accepts
     * smaller payloads on this version than on the v1 API: 512000 bytes, or 5242880 bytes
     * once decompressed for a compressed payload.
     */
    V2("/api/v2/series");

    private final String seriesPath;

    DatadogApiVersion(String seriesPath) {
        this.seriesPath = seriesPath;
    }

    String seriesPath() {
        return seriesPath;
    }

}
