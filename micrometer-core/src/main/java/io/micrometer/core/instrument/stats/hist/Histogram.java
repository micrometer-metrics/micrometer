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

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Jon Schneider
 */
public class Histogram<T> {
    private final NavigableMap<T, Bucket<T>> buckets = Collections.synchronizedNavigableMap(new TreeMap<T, Bucket<T>>());
    private final BucketFunction<? extends T> f;
    private final Type type;
    private final List<BucketListener<T>> bucketListeners;
    private final boolean percentiles;

    public Histogram(BucketFunction<? extends T> f, Type type, List<BucketListener<T>> listeners, boolean percentiles) {
        this.f = f;
        this.type = type;
        this.bucketListeners = listeners;
        this.percentiles = percentiles;
    }

    public Collection<Bucket<T>> getBuckets() {
        return buckets.values();
    }

    public boolean isCumulative() {
        return type.equals(Type.Cumulative);
    }

    public boolean isPercentiles() {
        return percentiles;
    }

    public Type getType() {
        return type;
    }

    public enum Type {
        Cumulative, Normal
    }

    public interface Builder<T> {
        /**
         * Typically called by a registry implementation, which knows what its base unit of time and
         * standard histogram type is.
         */
        Histogram<T> create(TimeUnit baseTimeUnit, Type defaultType);

        /**
         * Typically called by a registry implementation for dynamically adding bucket counters.
         */
        Builder bucketListener(BucketListener<T> listener);

        /**
         * Set the histogram type explicitly, overriding the monitoring system's default histogram type.
         */
        Builder type(Type type);

        /**
         * Hacky, but Atlas :percentiles math requires a different tag key than what we would place on
         * an ordinary histogram
         */
        Builder usedForPercentiles();
    }

    public static <U> Builder<U> function(BucketFunction<U> f) {
        return new DefaultHistogramBuilder<>(f);
    }

    /**
     * @param start Leftmost bucket
     * @param width The interval between nonZeroBuckets
     * @param count The total number of nonZeroBuckets, yielding {@code count}-1 intervals between them
     * @return Fixed-width nonZeroBuckets
     */
    public static Builder<Double> linear(double start, double width, int count) {
        return new DefaultHistogramBuilder<>(linearFunction(start, width, count));
    }

    /**
     * @param unit  The time unit of {@code start}.
     * @param start Leftmost bucket
     * @param width The interval between nonZeroBuckets
     * @param count The total number of nonZeroBuckets, yielding {@code count}-1 intervals between them
     * @return Fixed-width nonZeroBuckets time scaled
     */
    public static Builder<Double> linearTime(TimeUnit unit, double start, double width, int count) {
        return new TimeScalingHistogramBuilder(linearFunction(start, width, count), unit);
    }

    /**
     * @param start Leftmost bucket is start*factor
     * @param exp   The exponent
     * @param count The total number of nonZeroBuckets, yielding {@code count}-1 intervals between them
     * @return Exponential-width nonZeroBuckets
     */
    public static Builder<Double> exponential(double start, double exp, int count) {
        return new DefaultHistogramBuilder<>(exponentialFunction(start, exp, count));
    }

    /**
     * @param unit  The time unit of {@code start}.
     * @param start Leftmost bucket is start*factor
     * @param exp   The exponent
     * @param count The total number of nonZeroBuckets, yielding {@code count}-1 intervals between them
     * @return Exponential-width nonZeroBuckets
     */
    public static Builder<Double> exponentialTime(TimeUnit unit, double start, double exp, int count) {
        return new TimeScalingHistogramBuilder(exponentialFunction(start, exp, count), unit);
    }

    public static Builder<Double> percentiles() {
        return new DefaultHistogramBuilder<>(percentilesFunction()).usedForPercentiles();
    }

    public static Builder<Double> percentilesTime() {
        return new TimeScalingHistogramBuilder(percentilesFunction(), TimeUnit.NANOSECONDS).usedForPercentiles();
    }

    private static BucketFunction<Double> percentilesFunction() {
        return PercentileBuckets::bucketFunction;
    }

    private static BucketFunction<Double> linearFunction(double start, double width, int count) {
        return d -> {
            if (d > start + (width * (count - 1)))
                return Double.POSITIVE_INFINITY;
            return start + Math.ceil((d - start) / width) * width;
        };
    }

    private static BucketFunction<Double> exponentialFunction(double start, double exp, int count) {
        return d -> {
            if (d > Math.pow(exp, count - 1))
                return Double.POSITIVE_INFINITY;
            if (d - start <= 0)
                return start;

            double log = Math.log(d) / Math.log(exp);
            return Math.pow(exp, Math.ceil(log));
        };
    }

    public void observe(double value) {
        T tag = f.bucket(value);

        buckets.compute(tag, (t, b) -> {
            if (isCumulative()) {
                synchronized (buckets) {
                    buckets.tailMap(t).forEach((tailTag, bucket) -> bucket.increment());
                }
            }

            if (b == null) {
                Bucket<T> bucket;

                if (isCumulative()) {
                    Map.Entry<T, Bucket<T>> floor = buckets.floorEntry(tag);
                    bucket = new Bucket<>(tag, percentiles, floor == null ? 1 : floor.getValue().getValue() + 1);
                } else bucket = new Bucket<>(tag, percentiles, 1);

                bucketListeners.forEach(listener -> listener.bucketAdded(bucket));

                return bucket;
            } else
                return b.increment();
        });
    }
}