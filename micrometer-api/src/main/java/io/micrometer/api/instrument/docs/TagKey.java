/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.api.instrument.docs;

import java.util.Arrays;

import io.micrometer.api.instrument.Tag;

/**
 * Represents a tag key.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
public interface TagKey {

    /**
     * Merges arrays of tag keys.
     * @param tagKeys arrays of tag keys
     * @return a merged array of tag keys
     */
    static TagKey[] merge(TagKey[]... tagKeys) {
        return Arrays.stream(tagKeys).flatMap(Arrays::stream).toArray(TagKey[]::new);
    }

    /**
     * Returns tag key.
     *
     * @return tag key
     */
    String getKey();

    /**
     * Returns a tag for this {@code TagKey}.
     *
     * @param value value to append to this tag
     * @return tag
     */
    default Tag of(String value) {
        return Tag.of(getKey(), value);
    }
}
