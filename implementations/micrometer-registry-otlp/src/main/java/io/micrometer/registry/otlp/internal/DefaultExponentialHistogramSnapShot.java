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
package io.micrometer.registry.otlp.internal;

import static io.micrometer.registry.otlp.internal.ExponentialHistogramSnapShot.ExponentialBuckets.EMPTY_EXPONENTIAL_BUCKET;

import java.util.LinkedHashMap;
import java.util.Map;

class DefaultExponentialHistogramSnapShot implements ExponentialHistogramSnapShot {

    private static final int MAX_ENTRIES = 50;

    private static final Map<Integer, ExponentialHistogramSnapShot> emptySnapshotCache = new LinkedHashMap<Integer, ExponentialHistogramSnapShot>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private final int scale;

    private final long zeroCount;

    private final double zeroThreshold;

    private final ExponentialBuckets positive;

    private final ExponentialBuckets negative;

    DefaultExponentialHistogramSnapShot(int scale, long zeroCount, double zeroThreshold, ExponentialBuckets positive,
            ExponentialBuckets negative) {
        this.scale = scale;
        this.zeroCount = zeroCount;
        this.zeroThreshold = zeroThreshold;
        this.positive = positive;
        this.negative = negative;
    }

    @Override
    public int scale() {
        return scale;
    }

    @Override
    public long zeroCount() {
        return zeroCount;
    }

    @Override
    public ExponentialBuckets positive() {
        return positive;
    }

    @Override
    public ExponentialBuckets negative() {
        return negative;
    }

    @Override
    public double zeroThreshold() {
        return zeroThreshold;
    }

    @Override
    public boolean isEmpty() {
        return positive.isEmpty() && negative.isEmpty() && zeroCount == 0;
    }

    static ExponentialHistogramSnapShot getEmptySnapshotForScale(int scale) {
        return emptySnapshotCache.computeIfAbsent(scale, key -> new DefaultExponentialHistogramSnapShot(key, 0, 0,
                EMPTY_EXPONENTIAL_BUCKET, EMPTY_EXPONENTIAL_BUCKET));
    }

    @Override
    public String toString() {
        return "DefaultExponentialHistogramSnapShot{" + "scale=" + scale() + ", zero_count=" + zeroCount()
                + ", zero_threshold=" + zeroThreshold + ", positive={" + positive + "}, negative={" + negative + "}";
    }

}
