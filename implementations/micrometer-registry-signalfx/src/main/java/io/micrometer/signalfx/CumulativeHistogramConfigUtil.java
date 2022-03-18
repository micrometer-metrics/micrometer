/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.signalfx;

import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

import java.time.Duration;
import java.util.Arrays;

/**
 * Adds cumulative histogram capabilities to the {@link DistributionStatisticConfig}.
 *
 * @author Bogdan Drutu
 * @author Mateusz Rzeszutek
 */
final class CumulativeHistogramConfigUtil {

    static DistributionStatisticConfig updateConfig(DistributionStatisticConfig distributionStatisticConfig) {
        double[] sloBoundaries = distributionStatisticConfig.getServiceLevelObjectiveBoundaries();
        if (sloBoundaries == null || sloBoundaries.length == 0) {
            return distributionStatisticConfig;
        }
        double[] newSloBoundaries = sloBoundaries;
        // Add the +Inf bucket since the "count" resets every export.
        if (!isPositiveInf(sloBoundaries[sloBoundaries.length - 1])) {
            newSloBoundaries = Arrays.copyOf(sloBoundaries, sloBoundaries.length + 1);
            newSloBoundaries[newSloBoundaries.length - 1] = Double.MAX_VALUE;
        }

        return DistributionStatisticConfig.builder()
                // Set the expiration duration for the histogram counts to be effectively infinite.
                // Without this, the counts are reset every expiry duration.
                .expiry(Duration.ofNanos(Long.MAX_VALUE)) // effectively infinite
                .bufferLength(1)
                .serviceLevelObjectives(newSloBoundaries)
                .build()
                .merge(distributionStatisticConfig);
    }

    private static boolean isPositiveInf(double bucket) {
        return bucket == Double.POSITIVE_INFINITY || bucket == Double.MAX_VALUE || (long) bucket == Long.MAX_VALUE;
    }

    private CumulativeHistogramConfigUtil() {
    }
}
