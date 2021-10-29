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

import io.micrometer.core.lang.Nullable;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Immutable {@link Tag}.
 *
 * @author Jon Schneider
 */
public class ImmutableTag implements Tag {
    private final String key;
    private final String value;
    private final Cardinality cardinality;

    public ImmutableTag(String key, String value) {
        requireNonNull(key);
        requireNonNull(value);
        this.key = key;
        this.value = value;
        this.cardinality = Cardinality.LOW; // TODO: or HIGH?
    }

    public ImmutableTag(String key, String value, Cardinality cardinality) {
        requireNonNull(key);
        requireNonNull(value);
        requireNonNull(cardinality);
        this.key = key;
        this.value = value;
        this.cardinality = cardinality;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public Cardinality getCardinality() {
        return cardinality;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tag that = (Tag) o;
        return Objects.equals(key, that.getKey()) &&
            Objects.equals(value, that.getValue());
    }

    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + value.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "tag(" + key + "=" + value + ")";
    }
}
