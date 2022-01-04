/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.cloudwatch;

/**
 * Utilities for cloudwatch registry
 * @deprecated the micrometer-registry-cloudwatch implementation has been deprecated in favour of
 *             micrometer-registry-cloudwatch2, which uses AWS SDK for Java 2.x
 */
@Deprecated
final class CloudWatchUtils {

    /**
     * Minimum allowed value as specified by
     * {@link com.amazonaws.services.cloudwatch.model.MetricDatum#setValue(Double)}
     */
    private static final double MINIMUM_ALLOWED_VALUE = 8.515920e-109;

    /**
     * Maximum allowed value as specified by
     * {@link com.amazonaws.services.cloudwatch.model.MetricDatum#setValue(Double)}
     */
    // VisibleForTesting
    static final double MAXIMUM_ALLOWED_VALUE = 1.174271e+108;

    private CloudWatchUtils() {
    }

    /**
     * Clean up metric to be within the allowable range as specified in
     * {@link com.amazonaws.services.cloudwatch.model.MetricDatum#setValue(Double)}
     *
     * @param value unsanitized value
     * @return value clamped to allowable range
     */
    static double clampMetricValue(double value) {
        // Leave as is and let the SDK reject it
        if (Double.isNaN(value)) {
            return value;
        }
        double magnitude = Math.abs(value);
        if (magnitude == 0) {
            // Leave zero as zero
            return 0;
        }
        // Non-zero magnitude, clamp to allowed range
        double clampedMag = Math.min(Math.max(magnitude, MINIMUM_ALLOWED_VALUE), MAXIMUM_ALLOWED_VALUE);
        return Math.copySign(clampedMag, value);
    }

}
