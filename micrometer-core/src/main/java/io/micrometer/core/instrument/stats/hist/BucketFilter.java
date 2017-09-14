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
package io.micrometer.core.instrument.stats.hist;

/**
 * @author Jon Schneider
 */
public interface BucketFilter<T> {
    /**
     * Rejects buckets greater than {@code max}.
     */
    static <U extends Comparable<U>> BucketFilter<U> clampMax(U max) {
        return bucket -> bucket.getTag().compareTo(max) <= 0;
    }

    /**
     * Rejects buckets less than {@code min}.
     */
    static <U extends Comparable<U>> BucketFilter<U> clampMin(U min) {
        return bucket -> bucket.getTag().compareTo(min) >= 0;
    }

    boolean shouldPublish(Bucket<T> bucket);
}
