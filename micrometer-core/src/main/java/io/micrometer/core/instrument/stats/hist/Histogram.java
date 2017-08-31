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

    public Histogram(BucketFunction<? extends T> f, Type type, List<BucketListener<T>> listeners) {
        this.f = f;
        this.type = type;
        this.bucketListeners = listeners;
    }

    public Collection<Bucket<T>> getBuckets() {
        return buckets.values();
    }

    public boolean isCumulative() {
        return type.equals(Type.Cumulative);
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
        return new DefaultHistogramBuilder<>(percentilesFunction());
    }

    public static Builder<Double> percentilesTime() {
        return new TimeScalingHistogramBuilder(percentilesFunction(), TimeUnit.NANOSECONDS);
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
                    Map.Entry<T, Bucket<T>> ceiling = buckets.ceilingEntry(tag);
                    bucket = new Bucket<>(tag, ceiling == null ? 1 : ceiling.getValue().getValue() + 1);
                } else bucket = new Bucket<>(tag, 1);

                bucketListeners.forEach(listener -> listener.bucketAdded(bucket));

                return bucket;
            } else
                return b.increment();
        });
    }
}