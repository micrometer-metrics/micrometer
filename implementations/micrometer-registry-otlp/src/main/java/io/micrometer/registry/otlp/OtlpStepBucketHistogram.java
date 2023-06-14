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

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.StepBucketHistogram;

/**
 * This is an internal class not meant for general use. The only reason to have this class
 * is that components in this package can call {@code _closingRollover} on
 * {@code StepBucketHistogram} and the method does not need to be public.
 */
class OtlpStepBucketHistogram extends StepBucketHistogram {

    OtlpStepBucketHistogram(Clock clock, long stepMillis, DistributionStatisticConfig distributionStatisticConfig,
            boolean supportsAggregablePercentiles, boolean isCumulativeBucketCounts) {
        super(clock, stepMillis, distributionStatisticConfig, supportsAggregablePercentiles, isCumulativeBucketCounts);
    }

    @Override
    protected void _closingRollover() {
        super._closingRollover();
    }

}
