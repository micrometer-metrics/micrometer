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

import java.util.function.Function;
import java.util.function.Predicate;

import io.micrometer.common.docs.KeyName;
import io.micrometer.common.docs.Type;

/**
 * Key/value pair representing a dimension of a meter used to classify and drill into
 * measurements.
 *
 * @author Jon Schneider
 * @since 1.10.0
 */
public interface KeyValue extends Comparable<KeyValue> {

    /**
     * Use this if you want to indicate that the value is missing.
     */
    String NONE_VALUE = "none";

    String getKey();

    String getValue();

    default Type getType() {
        return Type.STRING;
    }

    default Object getTypedValue() {
        return getValue();
    }

    /**
     * Creates a {@link KeyValue} for the given key and value.
     * @param key key of the KeyValue
     * @param value value for key
     * @return KeyValue
     */
    static KeyValue of(String key, String value) {
        return new TypedKeyValue<>(key, value, Type.STRING);
    }

    /**
     * Creates a {@link KeyValue} for the given key and value.
     * @param key key of the KeyValue
     * @param value value for key
     * @return KeyValue
     */
    static TypedKeyValue<Boolean> of(String key, boolean value) {
        return new TypedKeyValue<>(key, value, Type.BOOLEAN);
    }

    /**
     * Creates a {@link KeyValue} for the given key and value.
     * @param key key of the KeyValue
     * @param value value for key
     * @return KeyValue
     */
    static TypedKeyValue<Double> of(String key, double value) {
        return new TypedKeyValue<>(key, value, Type.DOUBLE);
    }

    /**
     * Creates a {@link KeyValue} for the given key and value.
     * @param key key of the KeyValue
     * @param value value for key
     * @return KeyValue
     */
    static TypedKeyValue<Long> of(String key, long value) {
        return new TypedKeyValue<>(key, value, Type.LONG);
    }

    /**
     * Creates a {@link KeyValue} for the given {@link KeyName} and value.
     * @param keyName name of the key of the KeyValue
     * @param value value for key
     * @return KeyValue
     */
    static KeyValue of(KeyName keyName, double value) {
        return KeyValue.of(keyName.asString(), value);
    }

    /**
     * Creates a {@link KeyValue} for the given {@link KeyName} and value.
     * @param keyName name of the key of the KeyValue
     * @param value value for key
     * @return KeyValue
     */
    static KeyValue of(KeyName keyName, long value) {
        return KeyValue.of(keyName.asString(), value);
    }

    /**
     * Creates a {@link KeyValue} for the given {@link KeyName} and value.
     * @param keyName name of the key of the KeyValue
     * @param value value for key
     * @return KeyValue
     */
    static KeyValue of(KeyName keyName, boolean value) {
        return KeyValue.of(keyName.asString(), value);
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
     * Creates a {@link KeyValue} for the given {@code element} by extracting a key and
     * value from it.
     * @param element the source element
     * @param keyExtractor function to extract the key from the element
     * @param valueExtractor function to extract the value from the element
     * @return KeyValue
     */
    static <E> KeyValue of(E element, Function<E, String> keyExtractor, Function<E, String> valueExtractor) {
        return KeyValue.of(keyExtractor.apply(element), valueExtractor.apply(element));
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
