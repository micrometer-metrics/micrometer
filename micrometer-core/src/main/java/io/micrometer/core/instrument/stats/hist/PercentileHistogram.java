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

import java.util.Collection;

public class PercentileHistogram extends DefaultHistogram<Double> {
    public PercentileHistogram(BucketFunction<Double> f, Collection<BucketFilter<Double>> domainFilters, Summation summation) {
        super(f, domainFilters, summation);
    }

    public static class Builder extends Histogram.Builder<Double> {
        Builder() {
            super(PercentileBucketFunction.INSTANCE);
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
        public PercentileHistogram create(Summation defaultSummationMode) {
            return new PercentileHistogram(f, domainFilters, summation == null ? defaultSummationMode : summation);
        }
    }
}
