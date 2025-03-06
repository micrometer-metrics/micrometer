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
 * Histogram Flavor to be used while recording distributions,
 *
 * @see <a href=
 * "https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/sdk_exporters/otlp.md#additional-configuration">OTLP
 * Configuration</a>
 * @author Lenin Jaganathan
 * @since 1.14.0
 */
public enum HistogramFlavor {

    /**
     * Uses a pre-determined fixed bucketing strategy for histogram bucket boundaries.
     */
    EXPLICIT_BUCKET_HISTOGRAM,
    /**
     * Uses a base-2 exponential formula to determine bucket boundaries and an integer
     * scale parameter to control resolution. Implementations adjust scale as necessary
     * given the data.
     */
    BASE2_EXPONENTIAL_BUCKET_HISTOGRAM;

    /**
     * Converts a string to {@link HistogramFlavor} by using a case-insensitive matching.
     */
    public static HistogramFlavor fromString(final String histogramPreference) {
        if (BASE2_EXPONENTIAL_BUCKET_HISTOGRAM.name().equalsIgnoreCase(histogramPreference)) {
            return BASE2_EXPONENTIAL_BUCKET_HISTOGRAM;
        }
        return EXPLICIT_BUCKET_HISTOGRAM;
    }

}
