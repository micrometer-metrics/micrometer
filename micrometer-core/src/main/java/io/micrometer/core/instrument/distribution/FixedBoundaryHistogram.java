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
import java.util.concurrent.atomic.AtomicLongArray;

class FixedBoundaryHistogram {

    final AtomicLongArray values;

    private final double[] buckets;

    FixedBoundaryHistogram(double[] buckets) {
        this.buckets = buckets;
        this.values = new AtomicLongArray(buckets.length);
    }

    long[] atomicReadAndReset() {
        long[] snapshot = new long[values.length()];
        for (int i = 0; i < buckets.length; i++) {
            snapshot[i] = values.getAndSet(i, 0);
        }
        return snapshot;
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
    int leastLessThanOrEqualTo(long key) {
        int low = 0;
        int high = buckets.length - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (buckets[mid] < key)
                low = mid + 1;
            else if (buckets[mid] > key)
                high = mid - 1;
            else
                return mid; // exact match
        }

        return low < buckets.length ? low : -1;
    }

}
