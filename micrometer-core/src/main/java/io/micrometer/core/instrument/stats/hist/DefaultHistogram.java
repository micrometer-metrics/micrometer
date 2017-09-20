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
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class DefaultHistogram<T> implements Histogram<T> {
    protected final NavigableMap<T, Bucket<T>> buckets;
    private final BucketFunction<? extends T> f;
    private final Summation summation;
    private final Collection<BucketFilter<T>> domainFilters;

    public static class Builder<U> extends Histogram.Builder<U> {
        Builder(BucketFunction<U> f) {
            super(f);
        }

        @Override
        public Builder<U> summation(Summation summation) {
            return (Builder<U>) super.summation(summation);
        }

        @Override
        public Builder<U> filterBuckets(BucketFilter<U> filter) {
            return (Builder<U>) super.filterBuckets(filter);
        }

        @Override
        public DefaultHistogram<U> create(Summation defaultSummationMode) {
            return new DefaultHistogram<>(f, domainFilters, summation == null ? defaultSummationMode : summation);
        }
    }

    DefaultHistogram(BucketFunction<T> f, Collection<BucketFilter<T>> domainFilters, Summation summation) {
        this.f = f;
        this.summation = summation;
        this.domainFilters = domainFilters;
        this.buckets = f.buckets().stream().collect(
            toMap(
                Bucket::getTag,
                Function.identity(),
                (u, v) -> u,
                TreeMap::new
            )
        );
    }

    @Override
    public Collection<Bucket<T>> getBuckets() {
        if (domainFilters.isEmpty())
            return buckets.values();
        return buckets.values().stream()
            .filter(bucket -> domainFilters.stream().allMatch(filter -> filter.shouldPublish(bucket)))
            .collect(toList());
    }

    @Override
    public Bucket<T> getBucket(T tag) {
        return buckets.get(tag);
    }

    @Override
    public void observe(double value) {
        T tag = f.bucket(value);

        Bucket<T> bucket = buckets.get(tag);
        if (bucket != null)
            bucket.increment();

        if (isCumulative()) {
            buckets.tailMap(tag, false).forEach((tailTag, tailBucket) -> tailBucket.increment());
        }
    }

    @Override
    public boolean isCumulative() {
        return Summation.Cumulative.equals(summation);
    }
}
