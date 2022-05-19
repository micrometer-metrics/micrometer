/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.micrometer.conventions.common;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/** A builder of {@link Attributes} supporting an arbitrary number of key-value pairs. */
public interface AttributesBuilder {

    /** Create the {@link Attributes} from this. */
    Attributes build();

    /**
     * Puts a {@link AttributeKey} with associated value into this.
     *
     * <p>
     * The type parameter is unused.
     */
    // The type parameter was added unintentionally and unfortunately it is an API break
    // for
    // implementations of this interface to remove it. It doesn't affect users of the
    // interface in
    // any way, and has almost no effect on implementations, so we leave it until a future
    // major
    // version.
    <T> AttributesBuilder put(AttributeKey<Long> key, int value);

    /** Puts a {@link AttributeKey} with associated value into this. */
    <T> AttributesBuilder put(AttributeKey<T> key, T value);

    /**
     * Puts a String attribute into this.
     *
     * <p>
     * Note: It is strongly recommended to use {@link #put(AttributeKey, Object)}, and
     * pre-allocate your keys, if possible.
     * @return this Builder
     */
    default AttributesBuilder put(String key, String value) {
        return put(AttributeKey.stringKey(key), value);
    }

    /**
     * Puts a long attribute into this.
     *
     * <p>
     * Note: It is strongly recommended to use {@link #put(AttributeKey, Object)}, and
     * pre-allocate your keys, if possible.
     * @return this Builder
     */
    default AttributesBuilder put(String key, long value) {
        return put(AttributeKey.longKey(key), value);
    }

    /**
     * Puts a double attribute into this.
     *
     * <p>
     * Note: It is strongly recommended to use {@link #put(AttributeKey, Object)}, and
     * pre-allocate your keys, if possible.
     * @return this Builder
     */
    default AttributesBuilder put(String key, double value) {
        return put(AttributeKey.doubleKey(key), value);
    }

    /**
     * Puts a boolean attribute into this.
     *
     * <p>
     * Note: It is strongly recommended to use {@link #put(AttributeKey, Object)}, and
     * pre-allocate your keys, if possible.
     * @return this Builder
     */
    default AttributesBuilder put(String key, boolean value) {
        return put(AttributeKey.booleanKey(key), value);
    }

    /**
     * Puts a String array attribute into this.
     *
     * <p>
     * Note: It is strongly recommended to use {@link #put(AttributeKey, Object)}, and
     * pre-allocate your keys, if possible.
     * @return this Builder
     */
    default AttributesBuilder put(String key, String... value) {
        if (value == null) {
            return this;
        }
        return put(AttributeKey.stringArrayKey(key), Arrays.asList(value));
    }

    /**
     * Puts a List attribute into this.
     * @return this Builder
     */
    @SuppressWarnings("unchecked")
    default <T> AttributesBuilder put(AttributeKey<List<T>> key, T... value) {
        if (value == null) {
            return this;
        }
        return put(key, Arrays.asList(value));
    }

    /**
     * Puts a Long array attribute into this.
     *
     * <p>
     * Note: It is strongly recommended to use {@link #put(AttributeKey, Object)}, and
     * pre-allocate your keys, if possible.
     * @return this Builder
     */
    default AttributesBuilder put(String key, long... value) {
        if (value == null) {
            return this;
        }
        return put(AttributeKey.longArrayKey(key), ArrayBackedAttributesBuilder.toList(value));
    }

    /**
     * Puts a Double array attribute into this.
     *
     * <p>
     * Note: It is strongly recommended to use {@link #put(AttributeKey, Object)}, and
     * pre-allocate your keys, if possible.
     * @return this Builder
     */
    default AttributesBuilder put(String key, double... value) {
        if (value == null) {
            return this;
        }
        return put(AttributeKey.doubleArrayKey(key), ArrayBackedAttributesBuilder.toList(value));
    }

    /**
     * Puts a Boolean array attribute into this.
     *
     * <p>
     * Note: It is strongly recommended to use {@link #put(AttributeKey, Object)}, and
     * pre-allocate your keys, if possible.
     * @return this Builder
     */
    default AttributesBuilder put(String key, boolean... value) {
        if (value == null) {
            return this;
        }
        return put(AttributeKey.booleanArrayKey(key), ArrayBackedAttributesBuilder.toList(value));
    }

    /**
     * Puts all the provided attributes into this Builder.
     * @return this Builder
     */
    AttributesBuilder putAll(Attributes attributes);

    /**
     * Remove all attributes where {@link AttributeKey#getKey()} and
     * {@link AttributeKey#getType()} match the {@code key}.
     * @return this Builder
     */
    default <T> AttributesBuilder remove(AttributeKey<T> key) {
        // default implementation is no-op
        return this;
    }

    /**
     * Remove all attributes that satisfy the given predicate. Errors or runtime
     * exceptions thrown by the predicate are relayed to the caller.
     * @return this Builder
     */
    default AttributesBuilder removeIf(Predicate<AttributeKey<?>> filter) {
        // default implementation is no-op
        return this;
    }

}
