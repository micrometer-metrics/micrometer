/*
 * Copyright 2025 VMware, Inc.
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

import java.util.Objects;

import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;

/**
 * Immutable {@link KeyValue} with extended data.
 *
 * @author Seungyong Hong
 */
public class ImmutableExtendedKeyValue<T> implements KeyValue {

    private final String key;

    private final String value;

    private final T data;

    ImmutableExtendedKeyValue(String key, String value, T data) {
        requireNonNull(key);
        requireNonNull(value);
        requireNonNull(data);
        this.key = key;
        this.value = value;
        this.data = data;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getValue() {
        return value;
    }

    public T getData() {
        return data;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof ImmutableExtendedKeyValue<?>))
            return false;

        ImmutableExtendedKeyValue<?> immutableExtendedKeyValue = (ImmutableExtendedKeyValue<?>) object;
        return Objects.equals(key, immutableExtendedKeyValue.key)
                && Objects.equals(value, immutableExtendedKeyValue.value)
                && Objects.equals(data, immutableExtendedKeyValue.data);
    }

    @Override
    public int hashCode() {
        return hash(key, value, data);
    }

    @Override
    public String toString() {
        return "ImmutableExtendedKeyValue{" + "key='" + key + '\'' + ", value='" + value + '\'' + ", data=" + data
                + '}';
    }

}
