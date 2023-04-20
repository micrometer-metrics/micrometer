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
package io.micrometer.common;

import io.micrometer.common.docs.Type;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Key/value pair representing a typed dimension of a meter used to classify and drill
 * into measurements.
 *
 * @author Mikita Karaliou
 */
class TypedKeyValue<T> implements KeyValue {

    private final String key;

    private final T value;

    private final Type type;

    TypedKeyValue(String key, T value, Type type) {
        this.key = requireNonNull(key);
        this.value = requireNonNull(value);
        this.type = type;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getValue() {
        return String.valueOf(value);
    }

    @Override
    public Type getType() {
        return type;
    }

    public T getTypedValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TypedKeyValue<?> that = (TypedKeyValue<?>) o;
        return Objects.equals(key, that.key) && Objects.equals(value, that.value) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value, type);
    }

    public String toString() {
        return "keyValue(" + key + "=" + value + ")";
    }

}
