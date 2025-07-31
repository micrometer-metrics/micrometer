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

import java.util.Arrays;

/**
 * The CircularCountHolder is inspired from <a href=
 * "https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk/metrics/src/main/java/io/opentelemetry/sdk/metrics/internal/aggregator/AdaptingCircularBufferCounter.java">AdaptingCircularBufferCounter</a>
 * The adapting part is not implemented but the other aspects of it were used from the
 * AdaptingCircularBufferCounter.
 */
class CircularCountHolder {

    private final long[] counts;

    private final int length;

    private int startIndex;

    private int endIndex;

    private int baseIndex;

    CircularCountHolder(int size) {
        this.length = size;
        this.counts = new long[size];
        this.baseIndex = Integer.MIN_VALUE;
        this.startIndex = Integer.MIN_VALUE;
        this.endIndex = Integer.MIN_VALUE;
    }

    int getStartIndex() {
        return startIndex;
    }

    int getEndIndex() {
        return endIndex;
    }

    long getValueAtIndex(int index) {
        return counts[getRelativeIndex(index)];
    }

    boolean isEmpty() {
        return baseIndex == Integer.MIN_VALUE;
    }

    boolean increment(int index, long incrementBy) {
        if (baseIndex == Integer.MIN_VALUE) {
            this.baseIndex = index;
            this.startIndex = index;
            this.endIndex = index;
            this.counts[0] = this.counts[0] + incrementBy;
            return true;
        }

        if (index > endIndex) {
            if ((long) index - startIndex + 1 > length) {
                return false;
            }
            endIndex = index;
        }
        else if (index < startIndex) {
            if ((long) endIndex - index + 1 > length) {
                return false;
            }
            startIndex = index;
        }

        final int relativeIndex = getRelativeIndex(index);
        counts[relativeIndex] = counts[relativeIndex] + incrementBy;
        return true;
    }

    private int getRelativeIndex(int index) {
        int result = index - baseIndex;
        if (result >= length) {
            result -= length;
        }
        else if (result < 0) {
            result += length;
        }
        return result;
    }

    void reset() {
        Arrays.fill(counts, 0);
        this.baseIndex = Integer.MIN_VALUE;
        this.endIndex = Integer.MIN_VALUE;
        this.startIndex = Integer.MIN_VALUE;
    }

}
