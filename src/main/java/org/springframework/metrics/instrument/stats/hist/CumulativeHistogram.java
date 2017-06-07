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
package org.springframework.metrics.instrument.stats.hist;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

/**
 *
 * @author Jon Schneider
 */
public class CumulativeHistogram<T> implements Histogram<T> {
    protected final CumulativeBucketFunction<T> f;
    private final ConcurrentNavigableMap<T, Bucket<T>> buckets;

    public CumulativeHistogram(CumulativeBucketFunction<T> f) {
        this.f = f;
        this.buckets = f.bucketComparator() == null ? new ConcurrentSkipListMap<>() : new ConcurrentSkipListMap<>(f.bucketComparator());
        f.buckets().forEach(b -> this.buckets.put(b, new Bucket<>(b)));
    }

    @Override
    public void observe(double value) {
        buckets.tailMap(f.bucketFloor(value), true)
                .keySet()
                .forEach(k -> buckets.get(k).increment());
    }

    @Override
    public Collection<Bucket<T>> getBuckets() {
        return buckets.values();
    }

    public static <T> CumulativeHistogram<T> buckets(CumulativeBucketFunction<T> bucketFunction) {
        return new CumulativeHistogram<>(bucketFunction);
    }

    public static TimeScaleCumulativeHistogram buckets(CumulativeBucketFunction<Double> bucketFunction, TimeUnit timeScale) {
        return new TimeScaleCumulativeHistogram(bucketFunction, timeScale);
    }

    public static CumulativeBucketFunction<Double> exponential(double start, double exp, int count) {
        return fromDoubleStream(IntStream.rangeClosed(0, count - 1)
                .mapToDouble(n -> start * Math.pow(exp, n)));
    }

    public static CumulativeBucketFunction<Double> linear(double start, double width, int count) {
        return fromDoubleStream(IntStream.rangeClosed(0, count - 1)
                .mapToDouble(n -> start + width * n));
    }

    private static FixedCumulativeBucketFunction<Double> fromDoubleStream(DoubleStream stream) {
        Set<Double> buckets = stream.collect(HashSet::new, Set::add, Set::addAll);
        buckets.add(Double.POSITIVE_INFINITY);
        return new FixedCumulativeBucketFunction<>(d -> d, buckets, null);
    }
}
