/*
 * Copyright 2023 VMware, Inc.
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

/**
 * AggregationTemporality defines the way additive values are expressed.
 *
 * @see <a href=
 * "https://opentelemetry.io/docs/reference/specification/metrics/data-model/#temporality">OTLP
 * Temporality</a>
 * @author Lenin Jaganathan
 * @since 1.11.0
 */
public enum AggregationTemporality {

    /**
     * Reported values do not incorporate previous measurements.
     */
    DELTA,

    /**
     * Reported values incorporate previous measurements.
     */
    CUMULATIVE;

    static io.opentelemetry.sdk.metrics.data.AggregationTemporality toOtlpAggregationTemporality(
            AggregationTemporality aggregationTemporality) {
        switch (aggregationTemporality) {
            case DELTA:
                return io.opentelemetry.sdk.metrics.data.AggregationTemporality.DELTA;
            case CUMULATIVE:
            default:
                return io.opentelemetry.sdk.metrics.data.AggregationTemporality.CUMULATIVE;
        }
    }

    static boolean isDelta(AggregationTemporality aggregationTemporality) {
        return aggregationTemporality == AggregationTemporality.DELTA;
    }

    static boolean isCumulative(AggregationTemporality aggregationTemporality) {
        return aggregationTemporality == AggregationTemporality.CUMULATIVE;
    }

}
