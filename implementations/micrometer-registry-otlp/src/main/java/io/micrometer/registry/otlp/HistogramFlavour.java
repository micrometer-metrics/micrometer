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
 * Histogram Flavour to be used while recording distributions,
 *
 * @see <a href=
 * "https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/sdk_exporters/otlp.md#additional-configuration">OTLP
 * Configuration</a>
 * @author Lenin Jaganathan
 * @since 1.12.0
 */
public enum HistogramFlavour {

    EXPLICIT_BUCKET_HISTOGRAM, BASE2_EXPONENTIAL_BUCKET_HISTOGRAM;

    /**
     * Converts a string to {@link HistogramFlavour} by using a case-insensitive matching.
     */
    public static HistogramFlavour fromString(final String histogramPreference) {
        if (BASE2_EXPONENTIAL_BUCKET_HISTOGRAM.name().equalsIgnoreCase(histogramPreference)) {
            return BASE2_EXPONENTIAL_BUCKET_HISTOGRAM;
        }
        return EXPLICIT_BUCKET_HISTOGRAM;
    }

}
