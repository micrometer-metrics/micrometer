/**
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument;

import java.util.function.Supplier;

/**
 * Key/value pair representing a dimension of a meter used to classify and drill into measurements.
 *
 * @author Jon Schneider
 * @author Simon Scholz
 */
public interface Tag extends Comparable<Tag> {
    String getKey();

    String getValue();

    /**
     * Create a {@link Tag} instance with static values.
     *
     * @param key of the tag
     * @param value of the tag
     * @return an instance of {@link Tag}
     */
    static Tag of(String key, String value) {
        return new ImmutableTag(key, value);
    }

    /**
     * Create a {@link Tag} instance with a dynamic value, which may change during runtime.
     * <p>
     * NOTE: If the given valueSupplier returns null, an empty {@link String} will be returned as tag value.
     * </p>
     * <p>
     * This is especially handy when common tags, which might change during runtime, should be applied.
     * </p>
     * @param key of the tag
     * @param valueSupplier {@link Supplier}, which can determine the tag's value at runtime.
     *                       In case the given supplier returns null an empty {@link String} will be returned.
     * @return a {@link Tag} with a value, which might change during runtime
     */
    static Tag of(String key, Supplier<String> valueSupplier) {
        return new DynamicValueTag(key, valueSupplier);
    }

    @Override
    default int compareTo(Tag o) {
        return getKey().compareTo(o.getKey());
    }
}
