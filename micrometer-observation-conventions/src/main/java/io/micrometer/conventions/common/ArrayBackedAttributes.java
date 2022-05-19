/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.micrometer.conventions.common;

import java.util.ArrayList;
import java.util.Comparator;

final class ArrayBackedAttributes extends ImmutableKeyValuePairs<AttributeKey<?>, Object> implements Attributes {

    // We only compare the key name, not type, when constructing, to allow deduping keys
    // with the
    // same name but different type.
    private static final Comparator<AttributeKey<?>> KEY_COMPARATOR_FOR_CONSTRUCTION = Comparator
            .comparing(AttributeKey::getKey);

    static final Attributes EMPTY = Attributes.builder().build();

    private ArrayBackedAttributes(Object[] data, Comparator<AttributeKey<?>> keyComparator) {
        super(data, keyComparator);
    }

    /**
     * Only use this constructor if you can guarantee that the data has been de-duped,
     * sorted by key and contains no null values or null/empty keys.
     * @param data the raw data
     */
    ArrayBackedAttributes(Object[] data) {
        super(data);
    }

    @Override
    public AttributesBuilder toBuilder() {
        return new ArrayBackedAttributesBuilder(new ArrayList<>(data()));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(AttributeKey<T> key) {
        return (T) super.get(key);
    }

    static Attributes sortAndFilterToAttributes(Object... data) {
        // null out any empty keys or keys with null values
        // so they will then be removed by the sortAndFilter method.
        for (int i = 0; i < data.length; i += 2) {
            AttributeKey<?> key = (AttributeKey<?>) data[i];
            if (key != null && key.getKey().isEmpty()) {
                data[i] = null;
            }
        }
        return new ArrayBackedAttributes(data, KEY_COMPARATOR_FOR_CONSTRUCTION);
    }

}
