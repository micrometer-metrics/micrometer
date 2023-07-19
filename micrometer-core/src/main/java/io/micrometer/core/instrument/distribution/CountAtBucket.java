/*
 * Copyright 2017 VMware, Inc.
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

import io.micrometer.core.instrument.util.TimeUtils;

import java.util.concurrent.TimeUnit;

/**
 * The count of events less than or equal to a histogram bucket.
 *
 * @author Trustin Heuiseung Lee
 */
public final class CountAtBucket {

    private final double bucket;

    private final double count;

    /**
     * Create a {@code CountAtBucket} instance.
     * @param bucket bucket
     * @param count count
     * @deprecated Use {@link #CountAtBucket(double, double)} instead since 1.4.0.
     */
    @Deprecated
    public CountAtBucket(long bucket, double count) {
        this((double) bucket, count);
    }

    /**
     * Create a {@code CountAtBucket} instance.
     * @param bucket bucket
     * @param count count
     * @since 1.4.0
     */
    public CountAtBucket(double bucket, double count) {
        this.bucket = bucket;
        this.count = count;
    }

    public double bucket() {
        return bucket;
    }

    public double bucket(TimeUnit unit) {
        return TimeUtils.nanosToUnit(bucket, unit);
    }

    public double count() {
        return count;
    }

    boolean isPositiveInf() {
        // check for Long.MAX_VALUE to maintain backwards compatibility
        return bucket == Double.POSITIVE_INFINITY || bucket == Double.MAX_VALUE || (long) bucket == Long.MAX_VALUE;
    }

    @Override
    public String toString() {
        return "(" + count + " at " + bucket + ')';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        CountAtBucket that = (CountAtBucket) o;

        return Double.compare(that.bucket, bucket) == 0 && Double.compare(that.count, count) == 0;
    }

    @Override
    public int hashCode() {
        long tempBucket = Double.doubleToLongBits(bucket);
        int result = (int) (tempBucket ^ (tempBucket >>> 32));
        long tempCount = Double.doubleToLongBits(count);
        result = 31 * result + (int) (tempCount ^ (tempCount >>> 32));
        return result;
    }

}
