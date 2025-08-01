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
package io.micrometer.stackdriver;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.*;

final class StackdriverHistogramUtil {

    private StackdriverHistogramUtil() {
    }

    // copied and modified from AbstractDistributionSummary/AbstractTimer
    static Histogram stackdriverHistogram(Clock clock, DistributionStatisticConfig distributionStatisticConfig) {
        if (distributionStatisticConfig.isPublishingPercentiles()) {
            return new StackdriverClientSidePercentilesHistogram(clock, distributionStatisticConfig);
        }
        if (distributionStatisticConfig.isPublishingHistogram()) {
            return new StackdriverFixedBoundaryHistogram(clock, distributionStatisticConfig);
        }
        return new StackdriverInfinityBucketHistogram(clock, distributionStatisticConfig);
    }

    static class StackdriverClientSidePercentilesHistogram extends TimeWindowPercentileHistogram {

        StackdriverClientSidePercentilesHistogram(Clock clock,
                DistributionStatisticConfig distributionStatisticConfig) {
            super(clock, distributionStatisticConfig, true, false, true);
        }

    }

    static class StackdriverFixedBoundaryHistogram extends TimeWindowFixedBoundaryHistogram {

        StackdriverFixedBoundaryHistogram(Clock clock, DistributionStatisticConfig config) {
            super(clock, config, true, false, true);
        }

    }

    static class StackdriverInfinityBucketHistogram extends TimeWindowFixedBoundaryHistogram {

        StackdriverInfinityBucketHistogram(Clock clock, DistributionStatisticConfig config) {
            super(clock, config, false, false, true);
        }

    }

}
