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

import io.micrometer.common.docs.KeyName;

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

    /**
     * Creates a {@link KeyValue} for the given key and value.
     * @param key key of the KeyValue
     * @param value value for key
     * @return KeyValue
     */
    static KeyValue of(String key, String value) {
        return new ImmutableKeyValue(key, value);
    }

    /**
     * Creates a {@link KeyValue} for the given {@link KeyName} and value.
     * @param keyName name of the key of the KeyValue
     * @param value value for key
     * @return KeyValue
     */
    static KeyValue of(KeyName keyName, String value) {
        return KeyValue.of(keyName.asString(), value);
    }

    /**
     * Creates a {@link KeyValue} for the given key and value and additionally validates
     * it with the {@link Predicate}.
     * @param key key of the KeyValue
     * @param value value for key
     * @param validator the {@link Predicate} used for validating the value
     * @return KeyValue
     */
    static <T> KeyValue of(String key, T value, Predicate<? super T> validator) {
        return new ValidatedKeyValue<>(key, value, validator);
    }

    /**
     * Creates a {@link KeyValue} for the given {@link KeyName} and value and additionally
     * validates it with the {@link Predicate}.
     * @param keyName name of the key of the KeyValue
     * @param value value for key
     * @param validator the {@link Predicate} used for validating the value
     * @return KeyValue
     */
    static <T> KeyValue of(KeyName keyName, T value, Predicate<? super T> validator) {
        return KeyValue.of(keyName.asString(), value, validator);
    }

    @Override
    default int compareTo(KeyValue o) {
        return getKey().compareTo(o.getKey());
    }

}
