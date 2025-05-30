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

import io.micrometer.common.lang.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.joining;

/**
 * An immutable collection of {@link KeyValue KeyValues} that are guaranteed to be sorted
 * and deduplicated by key.
 *
 * @author Jon Schneider
 * @author Maciej Walkowiak
 * @author Phillip Webb
 * @author Johnny Lim
 * @since 1.10.0
 */
public final class KeyValues implements Iterable<KeyValue> {

    private static final KeyValues EMPTY = new KeyValues(new KeyValue[] {});

    private final KeyValue[] keyValues;

    private int last;

    private KeyValues(KeyValue[] keyValues) {
        this.keyValues = keyValues;
        Arrays.sort(this.keyValues);
        dedup();
    }

    private void dedup() {
        int n = keyValues.length;

        if (n == 0 || n == 1) {
            last = n;
            return;
        }

        // index of next unique element
        int j = 0;

        for (int i = 0; i < n - 1; i++)
            if (!keyValues[i].getKey().equals(keyValues[i + 1].getKey()))
                keyValues[j++] = keyValues[i];

        keyValues[j++] = keyValues[n - 1];
        last = j;
    }

    /**
     * Return a new {@code KeyValues} instance by merging this collection and the
     * specified key/value pair.
     * @param key the key to add
     * @param value the value to add
     * @return a new {@code KeyValues} instance
     */
    public KeyValues and(String key, String value) {
        return and(KeyValue.of(key, value));
    }

    /**
     * Return a new {@code KeyValues} instance by merging this collection and the
     * specified key/value pairs.
     * @param keyValues the key/value pairs to add, elements mustn't be null
     * @return a new {@code KeyValues} instance
     */
    public KeyValues and(@Nullable String... keyValues) {
        if (blankVarargs(keyValues)) {
            return this;
        }
        return and(KeyValues.of(keyValues));
    }

    /**
     * Return a new {@code KeyValues} instance by merging this collection and the
     * specified key values.
     * @param keyValues the key values to add, elements mustn't be null
     * @return a new {@code KeyValues} instance
     */
    public KeyValues and(@Nullable KeyValue... keyValues) {
        if (blankVarargs(keyValues)) {
            return this;
        }
        KeyValue[] newKeyValues = new KeyValue[last + keyValues.length];
        System.arraycopy(this.keyValues, 0, newKeyValues, 0, last);
        System.arraycopy(keyValues, 0, newKeyValues, last, keyValues.length);
        return new KeyValues(newKeyValues);
    }

    /**
     * Return a new {@code KeyValues} instance by merging this collection and the key
     * values extracted from the given elements.
     * @param elements the source elements
     * @param keyExtractor function to extract the key from the element
     * @param valueExtractor function to extract the value from the element
     * @return a new {@code KeyValues} instance
     */
    public <E> KeyValues and(@Nullable Iterable<E> elements, Function<E, String> keyExtractor,
            Function<E, String> valueExtractor) {
        if (elements == null || !elements.iterator().hasNext()) {
            return this;
        }
        Iterable<KeyValue> keyValues = () -> StreamSupport.stream(elements.spliterator(), false)
            .map(element -> KeyValue.of(element, keyExtractor, valueExtractor))
            .iterator();
        return and(keyValues);
    }

    /**
     * Return a new {@code KeyValues} instance by merging this collection and the
     * specified key values.
     * @param keyValues the key values to add, elements mustn't be null
     * @return a new {@code KeyValues} instance
     */
    public KeyValues and(@Nullable Iterable<? extends KeyValue> keyValues) {
        if (keyValues == null || keyValues == EMPTY) {
            return this;
        }
        else if (this.keyValues.length == 0) {
            return KeyValues.of(keyValues);
        }
        else if (!keyValues.iterator().hasNext()) {
            return this;
        }

        return and(KeyValues.of(keyValues).keyValues);
    }

    @Override
    public Iterator<KeyValue> iterator() {
        return new ArrayIterator();
    }

    private class ArrayIterator implements Iterator<KeyValue> {

        private int currentIndex = 0;

        @Override
        public boolean hasNext() {
            return currentIndex < last;
        }

        @Override
        public KeyValue next() {
            return keyValues[currentIndex++];
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("cannot remove items from key values");
        }

    }

    @Override
    public Spliterator<KeyValue> spliterator() {
        return Spliterators.spliterator(keyValues, 0, last, Spliterator.IMMUTABLE | Spliterator.ORDERED
                | Spliterator.DISTINCT | Spliterator.NONNULL | Spliterator.SORTED);
    }

    /**
     * Return a stream of the contained key values.
     * @return a key value stream
     */
    public Stream<KeyValue> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    @Override
    public int hashCode() {
        int result = 1;
        for (int i = 0; i < last; i++) {
            result = 31 * result + keyValues[i].hashCode();
        }
        return result;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return this == obj || (obj != null && getClass() == obj.getClass() && keyValuesEqual((KeyValues) obj));
    }

    private boolean keyValuesEqual(KeyValues obj) {
        if (keyValues == obj.keyValues)
            return true;

        if (last != obj.last)
            return false;

        for (int i = 0; i < last; i++) {
            if (!keyValues[i].equals(obj.keyValues[i]))
                return false;
        }

        return true;
    }

    /**
     * Return a new {@code KeyValues} instance by concatenating the specified collections
     * of key values.
     * @param keyValues the first set of key values, elements mustn't be null
     * @param otherKeyValues the second set of key values, elements mustn't be null
     * @return the merged key values
     */
    public static KeyValues concat(@Nullable Iterable<? extends KeyValue> keyValues,
            @Nullable Iterable<? extends KeyValue> otherKeyValues) {
        return KeyValues.of(keyValues).and(otherKeyValues);
    }

    /**
     * Return a new {@code KeyValues} instance by concatenating the specified key values
     * and key/value pairs.
     * @param keyValues the first set of key values, elements mustn't be null
     * @param otherKeyValues the additional key/value pairs to add, elements mustn't be
     * null
     * @return the merged key values
     */
    public static KeyValues concat(@Nullable Iterable<? extends KeyValue> keyValues,
            @Nullable String... otherKeyValues) {
        return KeyValues.of(keyValues).and(otherKeyValues);
    }

    /**
     * Return a new {@code KeyValues} instance containing key values extracted from the
     * given elements.
     * @param elements the source elements
     * @param keyExtractor function to extract the key from the element
     * @param valueExtractor function to extract the value from the element
     * @return a new {@code KeyValues} instance
     */
    public static <E> KeyValues of(@Nullable Iterable<E> elements, Function<E, String> keyExtractor,
            Function<E, String> valueExtractor) {
        return empty().and(elements, keyExtractor, valueExtractor);
    }

    /**
     * Return a new {@code KeyValues} instance containing key values constructed from the
     * specified source key values.
     * @param keyValues the key values to add, elements mustn't be null
     * @return a new {@code KeyValues} instance
     */
    public static KeyValues of(@Nullable Iterable<? extends KeyValue> keyValues) {
        if (keyValues == null || keyValues == EMPTY) {
            return KeyValues.empty();
        }
        else if (keyValues instanceof KeyValues) {
            return (KeyValues) keyValues;
        }
        else if (!keyValues.iterator().hasNext()) {
            return KeyValues.empty();
        }
        else if (keyValues instanceof Collection) {
            Collection<? extends KeyValue> keyValuesCollection = (Collection<? extends KeyValue>) keyValues;
            return new KeyValues(keyValuesCollection.toArray(new KeyValue[0]));
        }
        else {
            return new KeyValues(StreamSupport.stream(keyValues.spliterator(), false).toArray(KeyValue[]::new));
        }
    }

    /**
     * Return a new {@code KeyValues} instance containing key value constructed from the
     * specified key/value pair.
     * @param key the key to add
     * @param value the value to add
     * @return a new {@code KeyValues} instance
     */
    public static KeyValues of(String key, String value) {
        return new KeyValues(new KeyValue[] { KeyValue.of(key, value) });
    }

    /**
     * Return a new {@code KeyValues} instance containing key values constructed from the
     * specified key/value pairs.
     * @param keyValues the key/value pairs to add, elements mustn't be null
     * @return a new {@code KeyValues} instance
     */
    public static KeyValues of(@Nullable String... keyValues) {
        if (blankVarargs(keyValues)) {
            return empty();
        }
        if (keyValues.length % 2 == 1) {
            throw new IllegalArgumentException("size must be even, it is a set of key=value pairs");
        }
        KeyValue[] keyValueArray = new KeyValue[keyValues.length / 2];
        for (int i = 0; i < keyValues.length; i += 2) {
            keyValueArray[i / 2] = KeyValue.of(keyValues[i], keyValues[i + 1]);
        }
        return new KeyValues(keyValueArray);
    }

    private static boolean blankVarargs(@Nullable Object[] args) {
        return args == null || args.length == 0 || (args.length == 1 && args[0] == null);
    }

    /**
     * Return a new {@code KeyValues} instance containing key values constructed from the
     * specified key values.
     * @param keyValues the key values to add, elements mustn't be null
     * @return a new {@code KeyValues} instance
     */
    public static KeyValues of(@Nullable KeyValue... keyValues) {
        return empty().and(keyValues);
    }

    /**
     * Return a {@code KeyValues} instance that contains no elements.
     * @return an empty {@code KeyValues} instance
     */
    public static KeyValues empty() {
        return EMPTY;
    }

    @Override
    public String toString() {
        return stream().map(KeyValue::toString).collect(joining(",", "[", "]"));
    }

}
