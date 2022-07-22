/**
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.common;

import java.util.function.Predicate;

/**
 * {@link KeyValue} with value validation.
 *
 * @param <T> value type
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
class ValidatedKeyValue<T> implements KeyValue {

    private final String key;

    private final String value;

    ValidatedKeyValue(String key, T value, Predicate<? super T> validator) {
        this.key = key;
        this.value = String.valueOf(assertValue(value, validator));
    }

    @Override
    public String getKey() {
        return this.key;
    }

    @Override
    public String getValue() {
        return this.value;
    }

    private T assertValue(T value, Predicate<? super T> validator) {
        if (!validator.test(value)) {
            throw new IllegalArgumentException(
                    "Argument [" + value + "] does not follow required format for key [" + this.key + "]");
        }

        return value;
    }

    @Override
    public String toString() {
        return "keyValue(" + this.key + "=" + this.value + ")";
    }

}
