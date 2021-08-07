/**
 * Copyright 2021 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.cloudwatch2;

import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.lang.Nullable;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.utils.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Utilities for CloudWatch registry
 */
final class CloudWatchUtils {

    /**
     * Minimum allowed value as specified by
     * {@link MetricDatum#value()}
     */
    private static final double MINIMUM_ALLOWED_VALUE = 8.515920e-109;

    /**
     * Maximum allowed value as specified by
     * {@link MetricDatum#value()}
     */
    public static final double MAXIMUM_ALLOWED_VALUE = 1.174271e+108;

    private CloudWatchUtils() {
    }

    /**
     * Clean up metric to be within the allowable range as specified in
     * {@link MetricDatum#value()}
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

    //VisibleForTesting
    public static List<Pair<List<Double>, List<Double>>> histogramCountsToCloudWatchArrays(CountAtBucket[] histogramCounts, @Nullable TimeUnit baseTimeUnit) {
        List<Pair<List<Double>, List<Double>>> batches = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        List<Double> counts = new ArrayList<>();
        int lastCount = 0;
        for (CountAtBucket countAtBucket : histogramCounts) {
            final double c = countAtBucket.count();
            if (c - lastCount > 0) {
                if (baseTimeUnit == null) {
                    values.add(CloudWatchUtils.clampMetricValue(countAtBucket.bucket()));
                } else {
                    values.add(CloudWatchUtils.clampMetricValue(countAtBucket.bucket(baseTimeUnit)));

                }
                counts.add(c - lastCount);
                lastCount = (int) c;

                if (values.size() >= 150) {
                    batches.add(Pair.of(values, counts));
                    values = new ArrayList<>();
                    counts = new ArrayList<>();
                }
            }
        }

        if (values.size() > 0) {
            batches.add(Pair.of(values, counts));
        }

        return batches;
    }

}
