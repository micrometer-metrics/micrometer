/*
 * Copyright 2023 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.distribution;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLongArray;

class FixedBoundaryHistogram {

    final AtomicLongArray values;

    private final double[] buckets;

    private final boolean isCumulativeBucketCounts;

    FixedBoundaryHistogram(double[] buckets, boolean isCumulativeBucketCounts) {
        this.buckets = buckets;
        this.values = new AtomicLongArray(buckets.length);
        this.isCumulativeBucketCounts = isCumulativeBucketCounts;
    }

    long countAtValue(double value) {
        int index = Arrays.binarySearch(buckets, value);
        if (index < 0)
            return 0;
        return values.get(index);
    }

    void reset() {
        for (int i = 0; i < values.length(); i++) {
            values.set(i, 0);
        }
    }

    void record(long value) {
        int index = leastLessThanOrEqualTo(value);
        if (index > -1)
            values.incrementAndGet(index);
    }

    /**
     * The least bucket that is less than or equal to a sample.
     */
    int leastLessThanOrEqualTo(double key) {
        int low = 0;
        int high = buckets.length - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            double value = buckets[mid];
            if (value < key)
                low = mid + 1;
            else if (value > key)
                high = mid - 1;
            else
                return mid; // exact match
        }

        return low < buckets.length ? low : -1;
    }

    Iterator<CountAtBucket> countsAtValues(Iterator<Double> values) {
        return new Iterator<CountAtBucket>() {
            private double cumulativeCount = 0.0;

            @Override
            public boolean hasNext() {
                return values.hasNext();
            }

            @Override
            public CountAtBucket next() {
                double value = values.next();
                double count = countAtValue(value);
                if (isCumulativeBucketCounts) {
                    cumulativeCount += count;
                    return new CountAtBucket(value, cumulativeCount);
                }
                else {
                    return new CountAtBucket(value, count);
                }
            }
        };
    }

}
