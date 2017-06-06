/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.metrics.instrument.stats.hist;

import java.util.Comparator;
import java.util.Set;

public interface CumulativeBucketFunction<T> {
    /**
     * @return A value less than or equal to the first bucket that should be
     * incremented on account of the observation of {@code d} and strictly greater
     * than the last bucket that should NOT be incremented.
     */
    T bucketFloor(double d);

    Set<T> buckets();

    Comparator<? super T> bucketComparator();
}
