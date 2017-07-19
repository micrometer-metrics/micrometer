/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.stats.hist;

import io.micrometer.core.instrument.internal.TimeUtils;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A cumulative histogram whose bucket tag values represent a measure of time. This class preserves the dimension of this
 * time so that the histogram's bucket tags can be scaled to the base unit of time that the monitoring backend expects.
 *
 * Calls to {@link TimeScaleCumulativeHistogram#observe(double)} are expected to be made with a measure of time whose
 * scale matches this histogram's time scale.
 */
public class TimeScaleCumulativeHistogram extends CumulativeHistogram<Double> {
    private final TimeUnit timeScale;

    public TimeScaleCumulativeHistogram(CumulativeBucketFunction<Double> f, TimeUnit timeScale) {
        super(f);
        this.timeScale = timeScale;
    }

    /**
     * @param targetUnit The time scale of the new cumulative histogram
     * @return
     */
    public TimeScaleCumulativeHistogram shiftScale(TimeUnit targetUnit) {
        if(targetUnit.equals(timeScale))
            return this;
        return new TimeScaleCumulativeHistogram(new ScaledCumulativeBucketFunction(timeScale, targetUnit), targetUnit);
    }

    class ScaledCumulativeBucketFunction extends FixedCumulativeBucketFunction<Double> {
        ScaledCumulativeBucketFunction(TimeUnit sourceUnit, TimeUnit targetUnit) {
            super(d -> d, f.buckets().stream().map(d -> TimeUtils.convert(d, sourceUnit, targetUnit))
                    .collect(Collectors.toSet()), f.bucketComparator());
        }
    }
}
