package io.micrometer.core.instrument.stats.hist;

import io.micrometer.core.instrument.util.TimeUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Jon Schneider
 */
public class Histogram<T> {
    private NavigableMap<T, Bucket<T>> buckets = Collections.synchronizedNavigableMap(new TreeMap<T, Bucket<T>>());
    private final BucketFunction<? extends T> f;
    private final boolean cumulative;

    public Collection<Bucket<T>> getBuckets() {
        return buckets.values();
    }

    public boolean isCumulative() {
        return cumulative;
    }

    public Histogram(BucketFunction<? extends T> f, boolean cumulative) {
        this.f = f;
        this.cumulative = cumulative;
    }

    /**
     * A histogram configuration from which new histograms can be built.
     */
    public static class Config {
        private final TimeUnit baseTimeUnit;

        public Config(TimeUnit baseTimeUnit) {
            this.baseTimeUnit = baseTimeUnit;
        }

        public Histogram.Builder cumulative() {
            return new Histogram.Builder(baseTimeUnit, true);
        }

        public Histogram.Builder normal() {
            return new Histogram.Builder(baseTimeUnit, false);
        }

        public Histogram<Double> percentiles(boolean cumulativeBuckets) {
            throw new UnsupportedOperationException("Implement me!");
        }
    }

    public static class Builder {
        private final TimeUnit baseTimeUnit;
        private final boolean cumulative;

        Builder(TimeUnit baseTimeUnit, boolean cumulative) {
            this.baseTimeUnit = baseTimeUnit;
            this.cumulative = cumulative;
        }

        public <U> Histogram<U> function(BucketFunction<U> f) {
            return new Histogram<>(f, cumulative);
        }

        /**
         * @param start Leftmost bucket
         * @param width The interval between buckets
         * @param count The total number of buckets, yielding {@code count}-1 intervals between them
         * @return Fixed-width buckets
         */
        public Histogram<Double> linear(double start, double width, int count) {
            return new Histogram<>(linearFunction(start, width, count), cumulative);
        }

        /**
         * @param unit The time unit of {@code start}.
         * @param start Leftmost bucket
         * @param width The interval between buckets
         * @param count The total number of buckets, yielding {@code count}-1 intervals between them
         * @return Fixed-width buckets time scaled
         */
        public Histogram<Double> linearTime(TimeUnit unit, double start, double width, int count) {
            return new Histogram<>(timeScale(linearFunction(start, width, count), unit), cumulative);
        }

        /**
         * @param start Leftmost bucket is start*factor
         * @param exp The exponent
         * @param count The total number of buckets, yielding {@code count}-1 intervals between them
         * @return Exponential-width buckets
         */
        public Histogram<Double> exponential(double start, double exp, int count) {
            return new Histogram<>(exponentialFunction(start, exp, count), cumulative);
        }

        /**
         * @param unit The time unit of {@code start}.
         * @param start Leftmost bucket is start*factor
         * @param exp The exponent
         * @param count The total number of buckets, yielding {@code count}-1 intervals between them
         * @return Exponential-width buckets
         */
        public Histogram<Double> exponentialTime(TimeUnit unit, double start, double exp, int count) {
            return new Histogram<>(timeScale(exponentialFunction(start, exp, count), unit), cumulative);
        }

        public Histogram<Double> percentiles() {
            return new Histogram<>(percentilesFunction(), cumulative);
        }

        public Histogram<Double> percentilesTime() {
            return new Histogram<>(timeScale(percentilesFunction(), TimeUnit.NANOSECONDS), cumulative);
        }

        private BucketFunction<Double> percentilesFunction() {
            return PercentileBucketFunction::bucketFunction;
        }

        /**
         * @param f A bucket function scaled to {@code unit}
         * @param unit The scale of the function
         * @return A function scaled to {@code baseTimeUnit}
         */
        private BucketFunction<Double> timeScale(BucketFunction<Double> f, TimeUnit unit) {
            return observed -> {
                double unscaledBucket = f.bucket(TimeUtils.convert(observed, baseTimeUnit, unit));
                return TimeUtils.convert(unscaledBucket, unit, baseTimeUnit);
            };
        }

        private BucketFunction<Double> linearFunction(double start, double width, int count) {
            return d -> {
                if (d > start + (width * (count - 1)))
                    return Double.POSITIVE_INFINITY;
                return start + Math.ceil((d - start) / width) * width;
            };
        }

        private BucketFunction<Double> exponentialFunction(double start, double exp, int count) {
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

    public void observe(double value) {
        T tag = f.bucket(value);

        buckets.compute(tag, (t, b) -> {
            if(cumulative) {
                synchronized (buckets) {
                    buckets.tailMap(t).forEach((tailTag, bucket) -> bucket.increment());
                }
            }

            if (b == null) {
                if (cumulative) {
                    Map.Entry<T, Bucket<T>> ceiling = buckets.ceilingEntry(tag);
                    return new Bucket<>(tag, ceiling == null ? 1 : ceiling.getValue().getValue() + 1);
                } else return new Bucket<>(tag, 1);
            } else
                return b.increment();
        });
    }
}