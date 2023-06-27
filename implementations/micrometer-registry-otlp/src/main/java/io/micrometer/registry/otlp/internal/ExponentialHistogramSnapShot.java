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

import java.util.List;

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
     * Returns the index of the first entry in the positive Bucket counts list.
     */
    int offset();

    /**
     * Returns the count of positive range of exponential buckets.
     */
    List<Long> bucketsCount();

    /**
     * Returns the threshold below which (inclusive) the values are counted in
     * {@link ExponentialHistogramSnapShot#zeroCount()}.
     */
    double zeroThreshold();

    boolean isEmpty();

}
