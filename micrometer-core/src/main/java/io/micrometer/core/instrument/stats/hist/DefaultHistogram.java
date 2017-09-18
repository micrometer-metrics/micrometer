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

import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class DefaultHistogram<T> implements Histogram<T> {
    protected final NavigableMap<T, Bucket<T>> buckets = Collections.synchronizedNavigableMap(new TreeMap<T, Bucket<T>>());
    private final BucketFunction<? extends T> f;
    private final Summation summation;
    private final Collection<BucketFilter<T>> domainFilters = new ArrayList<>();

    public static class Builder<U> extends Histogram.Builder<U> {
        Builder(BucketFunction<U> f) {
            super(f);
        }

        @Override
        public DefaultHistogram<U> create(Summation defaultSummationMode) {
            return new DefaultHistogram<>(f, summation == null ? defaultSummationMode : summation);
        }
    }

    DefaultHistogram(BucketFunction<T> f, Summation summation) {
        this.f = f;
        this.summation = summation;
        this.buckets.putAll(f.buckets().stream().collect(toMap(Bucket::getTag, Function.identity())));
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
    public Histogram<T> filterBuckets(BucketFilter<T> filter) {
        domainFilters.add(filter);
        return this;
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
            synchronized (buckets) {
                buckets.tailMap(tag, false).forEach((tailTag, tailBucket) -> tailBucket.increment());
            }
        }
    }

    @Override
    public boolean isCumulative() {
        return Summation.Cumulative.equals(summation);
    }
}
