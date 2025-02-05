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

    private final boolean isCumulativeBucketCounts;

    /**
     * Creates a FixedBoundaryHistogram which tracks the count of values for each bucket
     * bound.
     * @param buckets sorted bucket boundaries
     * @param isCumulativeBucketCounts - whether the count values should be cumulative
     * count of lower buckets and current bucket.
     */
    FixedBoundaryHistogram(double[] buckets, boolean isCumulativeBucketCounts) {
        this.buckets = buckets;
        this.values = new AtomicLongArray(buckets.length);
        this.isCumulativeBucketCounts = isCumulativeBucketCounts;
    }

    double[] getBuckets() {
        return this.buckets;
    }

    /**
     * Returns the number of values that was recorded between previous bucket and the
     * queried bucket (upper bound inclusive).
     * @param bucket - the bucket to find values for
     * @return 0 if bucket is not a valid bucket otherwise number of values recorded
     * between (previous bucket, bucket]
     */
    private long countAtBucket(double bucket) {
        int index = Arrays.binarySearch(buckets, bucket);
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
     * The least bucket that is less than or equal to a valueToRecord. Returns -1, if the
     * valueToRecord is greater than the highest bucket.
     */
    // VisibleForTesting
    int leastLessThanOrEqualTo(double valueToRecord) {
        int low = 0;
        int high = buckets.length - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            double bucket = buckets[mid];
            if (bucket < valueToRecord)
                low = mid + 1;
            else if (bucket > valueToRecord)
                high = mid - 1;
            else
                return mid; // exact match
        }

        return low < buckets.length ? low : -1;
    }

    /**
     * Returns the array of {@link CountAtBucket} for each of the buckets tracked by this
     * histogram.
     */
    CountAtBucket[] getCountAtBuckets() {
        CountAtBucket[] countAtBuckets = new CountAtBucket[this.buckets.length];
        long cumulativeCount = 0;

        for (int i = 0; i < this.buckets.length; i++) {
            final long valueAtCurrentBucket = values.get(i);
            if (isCumulativeBucketCounts) {
                cumulativeCount += valueAtCurrentBucket;
                countAtBuckets[i] = new CountAtBucket(buckets[i], cumulativeCount);
            }
            else {
                countAtBuckets[i] = new CountAtBucket(buckets[i], valueAtCurrentBucket);
            }
        }
        return countAtBuckets;
    }

}
