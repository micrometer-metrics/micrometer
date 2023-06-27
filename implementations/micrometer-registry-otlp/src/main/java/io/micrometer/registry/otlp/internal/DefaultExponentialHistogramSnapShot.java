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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class DefaultExponentialHistogramSnapShot implements ExponentialHistogramSnapShot {

    private static final Map<Integer, ExponentialHistogramSnapShot> emptySnapshotCache = new HashMap<>();

    private final int scale;

    private final int offset;

    private final long zeroCount;

    private final double zeroThreshold;

    private final List<Long> bucketsCount;

    DefaultExponentialHistogramSnapShot(int scale, int offset, long zeroCount, double zeroThreshold,
            List<Long> bucketsCount) {
        this.scale = scale;
        this.offset = offset;
        this.zeroCount = zeroCount;
        this.zeroThreshold = zeroThreshold;
        this.bucketsCount = Collections.unmodifiableList(bucketsCount);
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
    public int offset() {
        return offset;
    }

    @Override
    public List<Long> bucketsCount() {
        return bucketsCount;
    }

    @Override
    public double zeroThreshold() {
        return zeroThreshold;
    }

    @Override
    public boolean isEmpty() {
        return bucketsCount.isEmpty() && zeroCount == 0;
    }

    static ExponentialHistogramSnapShot getEmptySnapshotForScale(int scale) {
        return emptySnapshotCache.computeIfAbsent(scale,
                key -> new DefaultExponentialHistogramSnapShot(key, 0, 0, 0.0, Collections.emptyList()));
    }

    @Override
    public String toString() {
        return "DefaultExponentialHistogramSnapShot{" + "scale=" + scale() + ", zeroCount=" + zeroCount()
                + ", zeroThreshold=" + zeroThreshold() + ", {bucketsCountLength=" + bucketsCount().size() + ", offset="
                + offset() + ", " + "bucketsCount=" + bucketsCount() + '}';
    }

}
