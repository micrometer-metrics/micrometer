/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.stats.hist;

import java.util.concurrent.TimeUnit;

public class PercentileTimeHistogram extends TimeHistogram {
    PercentileTimeHistogram(Histogram<Double> delegate, TimeUnit bucketTimeScale, TimeUnit fUnits) {
        super(delegate, bucketTimeScale, fUnits);
    }

    public static class Builder extends TimeHistogram.Builder {
        Builder(TimeUnit fUnits) {
            super(PercentileBucketFunction.INSTANCE, fUnits);
        }

        @Override
        public Builder summation(Summation summation) {
            return (Builder) super.summation(summation);
        }

        @Override
        public Builder filterBuckets(BucketFilter<Double> filter) {
            return (Builder) super.filterBuckets(filter);
        }

        @Override
        public Builder bucketTimeScale(TimeUnit bucketTimeScale) {
            return (Builder) super.bucketTimeScale(bucketTimeScale);
        }

        @Override
        public PercentileTimeHistogram create(Summation defaultSummationMode) {
            return new PercentileTimeHistogram(new DefaultHistogram<>(f, scaledDomainFilters(),
                summation == null ? defaultSummationMode : summation), bucketTimeScale, fUnits);
        }
    }
}
