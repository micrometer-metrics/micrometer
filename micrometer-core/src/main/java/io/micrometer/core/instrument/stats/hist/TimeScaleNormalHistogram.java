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

import java.util.concurrent.TimeUnit;

import static io.micrometer.core.instrument.util.TimeUtils.convert;

public class TimeScaleNormalHistogram extends NormalHistogram<Double> {
    private final TimeUnit timeScale;

    public TimeScaleNormalHistogram(BucketFunction<? extends Double> f, TimeUnit timeScale) {
        super(f);
        this.timeScale = timeScale;
    }

    /**
     * @param targetUnit The time scale of the new cumulative histogram
     * @return
     */
    public TimeScaleNormalHistogram shiftScale(TimeUnit targetUnit) {
        if(targetUnit.equals(timeScale))
            return this;
        return new TimeScaleNormalHistogram(new ScaledBucketFunction(timeScale, targetUnit), targetUnit);
    }

    class ScaledBucketFunction implements BucketFunction<Double> {
        private final TimeUnit targetUnit;
        private final TimeUnit sourceUnit;

        ScaledBucketFunction(TimeUnit sourceUnit, TimeUnit targetUnit) {
            this.sourceUnit = sourceUnit;
            this.targetUnit = targetUnit;
        }

        @Override
        public Double bucket(double d) {
            return convert(f.bucket(convert(d, targetUnit, sourceUnit)), sourceUnit, targetUnit);
        }
    }
}
