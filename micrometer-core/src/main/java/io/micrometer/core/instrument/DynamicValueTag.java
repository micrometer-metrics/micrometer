/**
 * Copyright 2020 VMware, Inc.
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

import io.micrometer.core.lang.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Dynamic value {@link Tag}.
 *
 * @author Simon Scholz
 */
final class DynamicValueTag implements Tag {

    private final String key;
    private final Supplier<String> valueSupplier;

    DynamicValueTag(String key, Supplier<String> valueSupplier) {
        this.key = requireNonNull(key, "The key must not be null");
        this.valueSupplier = requireNonNull(valueSupplier, "The valueSupplier must not be null");;
    }

    @Override
    public String getKey() {
        return key;
    }

    /**
     * Get the value of the given valueSupplier.
     * In case the valueSupplier returns null an empty String will be returned.
     *
     * @return the value of the given valueSupplier
     */
    @Override
    public String getValue() {
        String value = valueSupplier.get();
        return value != null ? value : "";
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || o.getClass().isAssignableFrom(Tag.class)) return false;
        Tag that = (Tag) o;
        return Objects.equals(key, that.getKey()) &&
                Objects.equals(valueSupplier.get(), that.getValue());
    }

    @Override
    public int hashCode() {
        int result = getKey().hashCode();
        result = 31 * result + getValue().hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "tag(" + getKey() + "=" + getValue() + ")";
    }
}
