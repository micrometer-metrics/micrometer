package org.springframework.metrics.instrument.stats;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.DoubleFunction;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

/**
 * A cumulative histogram. Sample values that are observed are counted to every bucket equal to or greater than the bucket
 * determined for the sample value according to {@link #f}.
 *
 * @author Jon Schneider
 */
class CumulativeHistogram<T> implements Histogram<T> {
    private final DoubleFunction<? extends T> f;
    private final ConcurrentNavigableMap<T, Double> buckets;

    public CumulativeHistogram(DoubleFunction<? extends T> f, Set<? extends T> buckets, Comparator<? super T> comp) {
        this.f = f;
        this.buckets = new ConcurrentSkipListMap<>(comp);
        buckets.forEach(b -> this.buckets.put(b, 0.0));
    }

    public CumulativeHistogram(DoubleFunction<? extends T> f, Set<? extends T> buckets) {
        this.f = f;
        this.buckets = new ConcurrentSkipListMap<>();
        buckets.forEach(b -> this.buckets.put(b, 0.0));
    }

    @Override
    public void observe(double value) {
        buckets.tailMap(f.apply(value), true)
                .keySet()
                .forEach(k -> buckets.merge(k, value, Double::sum));
    }

    @Override
    public Double get(T bucket) {
        return buckets.get(bucket);
    }

    @Override
    public Set<T> buckets() {
        return buckets.keySet();
    }

    /**
     * @param start Leftmost bucket
     * @param width The interval between buckets
     * @param count The total number of buckets, yielding count-1 intervals between them.
     * @return A cumulative histogram with fixed-width buckets.
     */
    public static CumulativeHistogram<Double> linear(double start, double width, int count) {
        return new CumulativeHistogram<>(d -> d, toSet(IntStream.rangeClosed(0, count-1)
                .mapToDouble(n -> start + width * n), Double.POSITIVE_INFINITY));
    }

    /**
     * @param start Leftmost bucket is start*factor.
     * @param exp The exponent.
     * @param count The total number of buckets, yielding count-1 intervals between them.
     * @return A cumulative histogram with exponential-width buckets.
     */
    public static CumulativeHistogram<Double> exponential(double start, double exp, int count) {
        return new CumulativeHistogram<>(d -> d, toSet(IntStream.rangeClosed(0, count-1)
                .mapToDouble(n -> start * Math.pow(exp, n)), Double.POSITIVE_INFINITY));
    }

    /**
     * @param buckets A set of buckets with any real values.
     * @return A cumulative histogram containing the defined buckets plus a bucket representing positive infinity.
     */
    public static CumulativeHistogram<Double> buckets(Double... buckets) {
        Set<Double> bucketSet = new HashSet<>(buckets.length);
        Collections.addAll(bucketSet, buckets);
        bucketSet.add(Double.POSITIVE_INFINITY);
        return new CumulativeHistogram<>(n -> n, bucketSet);
    }

    /**
     * @param f A bucket-generating function. Any values falling above the defined range {@code buckets} will be dropped.
     * @param buckets A fixed set of buckets, ordered by {@linkplain Comparable natural ordering}.
     * @return A cumulative histogram of an arbitrary type.
     */
    public static <T extends Comparable<T>> CumulativeHistogram<T> buckets(DoubleFunction<? extends T> f, T... buckets) {
        HashSet<T> bucketSet = new HashSet<>(buckets.length);
        Collections.addAll(bucketSet, buckets);
        return new CumulativeHistogram<>(f, bucketSet);
    }

    /**
     * @param f A bucket-generating function. Any values falling above the defined range {@code buckets} will be dropped.
     * @param ordering The comparator used to order the buckets.
     * @param buckets A fixed set of buckets.
     * @return A cumulative histogram of an arbitrary type.
     */
    public static <T extends Comparable<T>> CumulativeHistogram<T> buckets(DoubleFunction<? extends T> f, Comparator<? super T> ordering, T... buckets) {
        HashSet<T> bucketSet = new HashSet<>(buckets.length);
        Collections.addAll(bucketSet, buckets);
        return new CumulativeHistogram<>(f, bucketSet, ordering);
    }

    private static Set<Double> toSet(DoubleStream stream, Double... additionalBuckets) {
        Set<Double> buckets = stream.collect(HashSet::new, Set::add, Set::addAll);
        Collections.addAll(buckets, additionalBuckets);
        return buckets;
    }
}
