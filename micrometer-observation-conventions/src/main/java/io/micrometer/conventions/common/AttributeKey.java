/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.micrometer.conventions.common;

import java.util.List;

/**
 * This interface provides a handle for setting the values of {@link Attributes}. The type
 * of value that can be set with an implementation of this key is denoted by the type
 * parameter.
 *
 * <p>
 * Implementations MUST be immutable, as these are used as the keys to Maps.
 *
 * @param <T> The type of value that can be set with the key.
 */
@SuppressWarnings("rawtypes")
public interface AttributeKey<T> {

    /** Returns the underlying String representation of the key. */
    String getKey();

    /**
     * Returns the type of attribute for this key. Useful for building switch statements.
     */
    AttributeType getType();

    /** Returns a new AttributeKey for String valued attributes. */
    static AttributeKey<String> stringKey(String key) {
        return InternalAttributeKeyImpl.create(key, AttributeType.STRING);
    }

    /** Returns a new AttributeKey for Boolean valued attributes. */
    static AttributeKey<Boolean> booleanKey(String key) {
        return InternalAttributeKeyImpl.create(key, AttributeType.BOOLEAN);
    }

    /** Returns a new AttributeKey for Long valued attributes. */
    static AttributeKey<Long> longKey(String key) {
        return InternalAttributeKeyImpl.create(key, AttributeType.LONG);
    }

    /** Returns a new AttributeKey for Double valued attributes. */
    static AttributeKey<Double> doubleKey(String key) {
        return InternalAttributeKeyImpl.create(key, AttributeType.DOUBLE);
    }

    /** Returns a new AttributeKey for List&lt;String&gt; valued attributes. */
    static AttributeKey<List<String>> stringArrayKey(String key) {
        return InternalAttributeKeyImpl.create(key, AttributeType.STRING_ARRAY);
    }

    /** Returns a new AttributeKey for List&lt;Boolean&gt; valued attributes. */
    static AttributeKey<List<Boolean>> booleanArrayKey(String key) {
        return InternalAttributeKeyImpl.create(key, AttributeType.BOOLEAN_ARRAY);
    }

    /** Returns a new AttributeKey for List&lt;Long&gt; valued attributes. */
    static AttributeKey<List<Long>> longArrayKey(String key) {
        return InternalAttributeKeyImpl.create(key, AttributeType.LONG_ARRAY);
    }

    /** Returns a new AttributeKey for List&lt;Double&gt; valued attributes. */
    static AttributeKey<List<Double>> doubleArrayKey(String key) {
        return InternalAttributeKeyImpl.create(key, AttributeType.DOUBLE_ARRAY);
    }

}
