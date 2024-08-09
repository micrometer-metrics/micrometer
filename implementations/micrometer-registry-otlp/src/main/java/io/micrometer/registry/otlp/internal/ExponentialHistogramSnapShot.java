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
import java.util.List;

/**
 * <p>
 * <strong> This is an internal class and might have breaking changes, external
 * implementations SHOULD NOT rely on this implementation. </strong>
 * </p>
 *
 * @author Lenin Jaganathan
 * @since 1.14.0
 */
public interface ExponentialHistogramSnapShot {

    /**
     * Returns the scale of the ExponentialHistogram.
     */
    int scale();

    /**
     * Returns the count of values that are less than or equal to
     * {@link ExponentialHistogramSnapShot#zeroThreshold()}.
     */
    long zeroCount();

    /**
     * Returns the positive range of exponential bucket counts.
     */
    ExponentialBuckets positive();

    /**
     * Returns the negative range of exponential bucket counts.
     */
    ExponentialBuckets negative();

    /**
     * Returns the threshold below which (inclusive) the values are counted in
     * {@link ExponentialHistogramSnapShot#zeroCount()}.
     */
    double zeroThreshold();

    boolean isEmpty();

    /**
     * Represents a dense representation exponential bucket counts.
     */
    final class ExponentialBuckets {

        public static final ExponentialBuckets EMPTY_EXPONENTIAL_BUCKET = new ExponentialBuckets(0,
                Collections.emptyList());

        private final int offset;

        private final List<Long> bucketCounts;

        ExponentialBuckets(int offset, List<Long> bucketCounts) {
            this.offset = offset;
            this.bucketCounts = bucketCounts;
        }

        public int offset() {
            return offset;
        }

        public List<Long> bucketCounts() {
            return bucketCounts;
        }

        public boolean isEmpty() {
            return bucketCounts.isEmpty();
        }

        @Override
        public String toString() {
            return "offset=" + offset() + ", " + "bucketCounts=" + bucketCounts();
        }

    }

}
