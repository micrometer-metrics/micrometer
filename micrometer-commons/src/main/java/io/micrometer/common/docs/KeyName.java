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
package io.micrometer.common.docs;

import java.util.Arrays;
import java.util.function.Predicate;

import io.micrometer.common.KeyValue;

/**
 * Represents a key name used for documenting instrumentation.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public interface KeyName {

    /**
     * Merges arrays of tag keys.
     * @param keyNames arrays of tag keys
     * @return a merged array of tag keys
     */
    static KeyName[] merge(KeyName[]... keyNames) {
        return Arrays.stream(keyNames).flatMap(Arrays::stream).toArray(KeyName[]::new);
    }

    /**
     * Creates a key value for the given key name.
     * @param value value for key
     * @return key value
     */
    default KeyValue withValue(String value) {
        return KeyValue.of(this, value);
    }

    /**
     * Creates a key value for the given key name.
     * @param value value for key
     * @param validator value validator
     * @return key value
     */
    default KeyValue withValue(String value, Predicate<Object> validator) {
        return KeyValue.of(this, value, validator);
    }

    /**
     * Returns key name.
     * @return key name
     */
    String asString();

    /**
     * Whether this key is required to be present in the instrumentation. This can be
     * checked in a test of the instrumentation.
     * @return whether this key is required
     */
    default boolean isRequired() {
        return true;
    }

}
