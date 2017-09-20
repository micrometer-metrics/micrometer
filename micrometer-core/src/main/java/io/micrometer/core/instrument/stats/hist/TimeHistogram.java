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

import io.micrometer.core.instrument.util.TimeUtils;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Jon Schneider
 */
public class TimeHistogram implements Histogram<Double> {
    private final Histogram<Double> delegate;
    private final TimeUnit fUnits;
    private TimeUnit bucketTimeScale = TimeUnit.NANOSECONDS;

    TimeHistogram(Histogram<Double> delegate, TimeUnit fUnits) {
        this.delegate = delegate;
        this.fUnits = fUnits;
    }

    public static class Builder extends Histogram.Builder<Double> {
        private final TimeUnit fUnits;

        Builder(BucketFunction<Double> f, TimeUnit fUnits) {
            super(f);
            this.fUnits = fUnits;
        }

        @Override
        public TimeHistogram create(Summation defaultSummationMode) {
            return new TimeHistogram(new DefaultHistogram<>(f, summation == null ? defaultSummationMode : summation),
                fUnits);
        }
    }

    /**
     * Set the time scale that the buckets should be represented in. For example, if the bucket function is a linear
     * function from 1-10 ms and the {@code bucketTimeScale} is seconds, then the buckets that are reported will be
     * [0.001, ..., 0.01]. Future values observed by this histogram will also be assumed to be in {@code bucketTimeScale}
     * units and scaled to the bucket function's base unit.
     *
     * @param bucketTimeScale Should always correspond to the base time unit of the monitoring system for consistency.
     */
    public void bucketTimeScale(TimeUnit bucketTimeScale) {
        this.bucketTimeScale = bucketTimeScale;
    }

    @Override
    public Collection<Bucket<Double>> getBuckets() {
        return delegate.getBuckets().stream()
            .map(this::scaled)
            .collect(Collectors.toList());
    }

    @Override
    public Histogram<Double> filterBuckets(BucketFilter<Double> filter) {
        return delegate.filterBuckets(bucket -> filter.shouldPublish(scaled(bucket)));
    }

    @Override
    public Bucket<Double> getBucket(Double tag) {
        return delegate.getBucket(tag);
    }

    @Override
    public void observe(double value) {
        delegate.observe(TimeUtils.convert(value, bucketTimeScale, fUnits));
    }

    @Override
    public boolean isCumulative() {
        return delegate.isCumulative();
    }

    private Bucket<Double> scaled(Bucket<Double> unscaledBucket) {
        return new Bucket<Double>(TimeUtils.convert(unscaledBucket.getTag(), fUnits, bucketTimeScale), unscaledBucket.getIndex()) {
            @Override
            public long getValue() {
                return unscaledBucket.getValue();
            }
        };
    }
}
