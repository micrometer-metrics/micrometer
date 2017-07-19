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

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * A non-cumulative histogram. For discrete data that requires sensible breaks.
 *
 * @author Jon Schneider
 */
public class NormalHistogram<T> implements Histogram<T> {
    protected final BucketFunction<? extends T> f;
    private final Map<T, Bucket<T>> buckets = new ConcurrentHashMap<>();

    public NormalHistogram(BucketFunction<? extends T> f) {
        this.f = f;
    }

    @Override
    public void observe(double value) {
        T tag = f.bucket(value);
        buckets.compute(tag, (t, b) -> b == null ? new Bucket<>(tag, 1) : b.increment());
    }

    @Override
    public Collection<Bucket<T>> getBuckets() {
        return buckets.values();
    }

    public static <T> NormalHistogram<T> buckets(BucketFunction<T> bucketFunction) {
        return new NormalHistogram<>(bucketFunction);
    }

    public static TimeScaleNormalHistogram buckets(BucketFunction<Double> bucketFunction, TimeUnit timeScale) {
        return new TimeScaleNormalHistogram(bucketFunction, timeScale);
    }

    /**
     * @param start Leftmost bucket
     * @param width The interval between buckets
     * @param count The total number of buckets, yielding {@code count}-1 intervals between them.
     * @return Fixed-width buckets.
            */
    public static BucketFunction<Double> linear(double start, double width, int count) {
        return d -> {
            if (d > start + (width * (count - 1)))
                return Double.POSITIVE_INFINITY;
            return start + Math.ceil((d - start) / width) * width;
        };
    }

    /**
     * @param start Leftmost bucket is start*factor.
     * @param exp The exponent.
     * @param count The total number of buckets, yielding {@code count}-1 intervals between them.
     * @return Exponential-width buckets.
     */
    public static BucketFunction<Double> exponential(double start, double exp, int count) {
        return d -> {
            if (d > Math.pow(exp, count - 1))
                return Double.POSITIVE_INFINITY;
            if (d - start <= 0)
                return start;

            double log = Math.log(d) / Math.log(exp);
            return Math.pow(exp, Math.ceil(log));
        };
    }
}