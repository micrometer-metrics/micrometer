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
package org.springframework.metrics.instrument.stats;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleFunction;

/**
 * A non-cumulative histogram.
 *
 * @author Jon Schneider
 */
public class NormalHistogram<T> implements Histogram<T> {
    private final DoubleFunction<? extends T> f;
    private final Map<T, Double> buckets = new ConcurrentHashMap<>();

    public NormalHistogram(DoubleFunction<? extends T> f) {
        this.f = f;
    }

    @Override
    public void observe(double value) {
        buckets.merge(f.apply(value), value, Double::sum);
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
     * @return A normal histogram with fixed-width buckets.
     */
    public static Histogram<Double> linear(double start, double width, int count) {
        return new NormalHistogram<>(d -> {
            if (d > start + (width * (count - 1)))
                return Double.POSITIVE_INFINITY;
            return start + Math.ceil((d - start) / width) * width;
        });
    }

    /**
     * @param start Leftmost bucket is start*factor.
     * @param exp The exponent.
     * @param count The total number of buckets, yielding count-1 intervals between them.
     * @return A normal histogram with exponential-width buckets.
     */
    public static NormalHistogram<Double> exponential(double start, double exp, int count) {
        return new NormalHistogram<>(d -> {
            if (d > Math.pow(exp, count-1))
                return Double.POSITIVE_INFINITY;
            if (d - start <= 0)
                return start;

            double log = Math.log(d) / Math.log(exp);
            return Math.pow(exp, Math.ceil(log));
        });
    }

    /**
     * @param f A bucket-generating function. It is important that this generates a low-cardinality range so as not
     *          to overwhelm the metrics backend with too many time series.
     * @return A normal histogram with buckets that are generated as they are emitted from {@code f} based on sample observations.
     */
    public static <T extends Comparable<T>> NormalHistogram<T> buckets(DoubleFunction<? extends T> f) {
        return new NormalHistogram<>(f);
    }
}