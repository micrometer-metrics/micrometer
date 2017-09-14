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

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * @author Jon Schneider
 */
public interface Histogram<T> {
    Collection<Bucket<T>> getBuckets();
    Histogram<T> filterBuckets(BucketFilter<T> filter);
    Bucket<T> getBucket(T tag);
    void observe(double value);
    boolean isCumulative();

    enum Summation {
        Cumulative,
        Normal
    }

    abstract class Builder<T> {
        BucketFunction<T> f;
        Summation summation = null;

        Builder(BucketFunction<T> f) {
            this.f = f;
        }

        public Builder<T> summation(Summation summation) {
            this.summation = summation;
            return this;
        }

        public abstract Histogram<T> create(Summation defaultSummationMode);
    }

    static <U> DefaultHistogram.Builder<U> function(BucketFunction<U> f) {
        return new DefaultHistogram.Builder<>(f);
    }

    /**
     * @param start Leftmost bucket
     * @param width The interval between nonZeroBuckets
     * @param count The total number of nonZeroBuckets, yielding {@code count}-1 intervals between them
     * @return Fixed-width nonZeroBuckets
     */
    static DefaultHistogram.Builder<Double> linear(double start, double width, int count) {
        return new DefaultHistogram.Builder<>(linearFunction(start, width, count));
    }

    /**
     * @param unit  The time unit of {@code start}.
     * @param start Leftmost bucket
     * @param width The interval between nonZeroBuckets
     * @param count The total number of nonZeroBuckets, yielding {@code count}-1 intervals between them
     * @return Fixed-width nonZeroBuckets time scaled
     */
    static TimeHistogram.Builder linearTime(TimeUnit unit, double start, double width, int count) {
        return new TimeHistogram.Builder(linearFunction(start, width, count), unit);
    }

    /**
     * @param start Leftmost bucket is start*factor
     * @param exp   The exponent
     * @param count The total number of nonZeroBuckets, yielding {@code count}-1 intervals between them
     * @return Exponential-width nonZeroBuckets
     */
    static DefaultHistogram.Builder<Double> exponential(double start, double exp, int count) {
        return new DefaultHistogram.Builder<>(exponentialFunction(start, exp, count));
    }

    /**
     * @param unit  The time unit of {@code start}.
     * @param start Leftmost bucket is start*factor
     * @param exp   The exponent
     * @param count The total number of nonZeroBuckets, yielding {@code count}-1 intervals between them
     * @return Exponential-width nonZeroBuckets
     */
    static TimeHistogram.Builder exponentialTime(TimeUnit unit, double start, double exp, int count) {
        return new TimeHistogram.Builder(exponentialFunction(start, exp, count), unit);
    }

    static PercentileHistogram.Builder percentiles() {
        return new PercentileHistogram.Builder();
    }

    static PercentileTimeHistogram.Builder percentilesTime() {
        return new PercentileTimeHistogram.Builder(TimeUnit.NANOSECONDS);
    }

    // VisibleForTesting
    static BucketFunction<Double> linearFunction(double start, double width, int count) {
        return new BucketFunction<Double>() {
            @Override
            public Double bucket(double d) {
                if (d > start + (width * (count - 1)))
                    return Double.POSITIVE_INFINITY;
                return start + Math.ceil((d - start) / width) * width;
            }

            @Override
            public Collection<Bucket<Double>> buckets() {
                Collection<Bucket<Double>> domain = new ArrayList<>();
                for(int n = 0; n < count; n++)
                    domain.add(new Bucket<>(start + (n*width), n));
                domain.add(new Bucket<>(Double.POSITIVE_INFINITY, domain.size()));
                return domain;
            }
        };
    }

    // VisibleForTesting
    static BucketFunction<Double> exponentialFunction(double start, double exp, int count) {
        return new BucketFunction<Double>() {
            @Override
            public Double bucket(double d) {
                if (d > Math.pow(exp, count - 1))
                    return Double.POSITIVE_INFINITY;
                if (d - start <= 0)
                    return start;

                double log = Math.log(d) / Math.log(exp);
                return Math.pow(exp, Math.ceil(log));
            }

            @Override
            public Collection<Bucket<Double>> buckets() {
                Collection<Bucket<Double>> domain = new ArrayList<>();
                for(int n = 0; n < count; n++)
                    domain.add(new Bucket<>(start * Math.pow(exp, n), n));
                domain.add(new Bucket<>(Double.POSITIVE_INFINITY, domain.size()));
                return domain;
            }
        };
    }
}