/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.common;

import java.util.function.Predicate;

/**
 * Key/value pair representing a dimension of a meter used to classify and drill into
 * measurements.
 *
 * @author Jon Schneider
 * @since 1.10.0
 */
public interface KeyValue extends Comparable<KeyValue> {

    String getKey();

    String getValue();

    static KeyValue of(String key, String value) {
        return new ImmutableKeyValue(key, value);
    }

    static KeyValue of(String key, Object value, Predicate<Object> validator) {
        return new ValidatedKeyValue<>(key, value, validator);
    }

    static KeyValue ofUnknownValue(String key) {
        return KeyValue.of(key, "UNKNOWN");
    }

    @Override
    default int compareTo(KeyValue o) {
        return getKey().compareTo(o.getKey());
    }

}
