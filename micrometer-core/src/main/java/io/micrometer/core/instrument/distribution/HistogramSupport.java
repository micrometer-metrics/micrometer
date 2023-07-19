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
package io.micrometer.core.instrument.distribution;

import io.micrometer.core.instrument.Meter;

public interface HistogramSupport extends Meter {

    /**
     * Summary statistics should be published off of a single snapshot instance so that,
     * for example, there isn't disagreement between the distribution's bucket counts
     * because more events continue to stream in.
     * @return A snapshot of all distribution statistics at a point in time.
     */
    HistogramSnapshot takeSnapshot();

    /**
     * Summary statistics should be published off of a single snapshot instance so that,
     * for example, there isn't disagreement between the distribution's bucket counts
     * because more events continue to stream in.
     * @param supportsAggregablePercentiles Ignored. The determination of aggregable
     * percentile support is now made up front.
     * @return A snapshot of all distribution statistics at a point in time.
     * @deprecated Use {@link #takeSnapshot()}.
     */
    @Deprecated
    default HistogramSnapshot takeSnapshot(boolean supportsAggregablePercentiles) {
        return takeSnapshot();
    }

}
