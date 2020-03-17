/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
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

    private final boolean isLong;

    public CountAtBucket(double bucket, double count) {
        this(bucket, count, true);
    }

    public CountAtBucket(double bucket, double count, boolean isLong) {
        this.bucket = bucket;
        this.count = count;
        this.isLong = isLong;
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

    public boolean isPositiveInf() {
        if (isLong)
            return bucket == (double)Long.MAX_VALUE;
        else
            return bucket == Double.POSITIVE_INFINITY;
    }

    @Override
    public String toString() {
        return "(" + count + " at " + bucket + ')';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CountAtBucket that = (CountAtBucket) o;

        return Double.compare(that.bucket, bucket) == 0 && Double.compare(that.count, count) == 0;
    }

    @Override
    public int hashCode() {
        int result;
        long tempCount, tempBucket;
        tempBucket = Double.doubleToLongBits(bucket);
        result = (int) (tempBucket ^ (tempBucket >>> 32));
        tempCount = Double.doubleToLongBits(count);
        result = 31 * result + (int) (tempCount ^ (tempCount >>> 32));
        return result;
    }
}
