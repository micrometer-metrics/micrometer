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

public enum AggregationTemporality {

    DELTA, CUMULATIVE;

    public static io.opentelemetry.proto.metrics.v1.AggregationTemporality mapToOtlp(
            AggregationTemporality aggregationTemporality) {
        switch (aggregationTemporality) {
            case DELTA:
                return io.opentelemetry.proto.metrics.v1.AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA;
            case CUMULATIVE:
                return io.opentelemetry.proto.metrics.v1.AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE;
            default:
                return io.opentelemetry.proto.metrics.v1.AggregationTemporality.UNRECOGNIZED;
        }
    }

}
