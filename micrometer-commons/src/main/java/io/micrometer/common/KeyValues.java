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

    private static final KeyValues EMPTY = new KeyValues(new KeyValue[] {}, 0);

    /**
     * A private array of {@code KeyValue} objects containing the sorted and deduplicated
     * tags.
     */
    private final KeyValue[] sortedSet;

    /**
     * The number of valid tags present in the {@link #sortedSet} array.
     */
    private final int length;

    /**
     * A private constructor that initializes a {@code KeyValues} object with a sorted set
     * of keyvalues and its length.
     * @param sortedSet an ordered set of unique keyvalues by key
     * @param length the number of valid tags in the {@code sortedSet}
     */
    private KeyValues(KeyValue[] sortedSet, int length) {
        this.sortedSet = sortedSet;
        this.length = length;
    }

    /**
     * Checks if the first {@code length} elements of the {@code keyvalues} array form an
     * ordered set of keyvalues.
     * @param keyValues an array of keyvalues.
     * @param length the number of items to check.
     * @return {@code true} if the first {@code length} items of {@code keyvalues} form an
     * ordered set; otherwise {@code false}.
     */
    private static boolean isSortedSet(KeyValue[] keyValues, int length) {
        if (length > keyValues.length) {
            return false;
        }
        for (int i = 0; i < length - 1; i++) {
            int cmp = keyValues[i].compareTo(keyValues[i + 1]);
            if (cmp >= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Constructs a {@code Tags} collection from the provided array of tags.
     * @param keyValues an array of {@code Tag} objects, possibly unordered and/or
     * containing duplicates.
     * @return a {@code Tags} instance with a deduplicated and ordered set of tags.
     */
    private static KeyValues make(KeyValue[] keyValues) {
        int len = keyValues.length;
        if (!isSortedSet(keyValues, len)) {
            Arrays.sort(keyValues);
            len = dedup(keyValues);
        }
        return new KeyValues(keyValues, len);
    }

    /**
     * Removes duplicate tags from an ordered array of tags.
     * @param keyValues an ordered array of {@code Tag} objects.
     * @return the number of unique tags in the {@code tags} array after removing
     * duplicates.
     */
    private static int dedup(KeyValue[] keyValues) {
        int n = keyValues.length;

        if (n == 0 || n == 1) {
            return n;
        }

        // index of next unique element
        int j = 0;

        for (int i = 0; i < n - 1; i++)
            if (!keyValues[i].getKey().equals(keyValues[i + 1].getKey()))
                keyValues[j++] = keyValues[i];

        keyValues[j++] = keyValues[n - 1];
        return j;
    }

    /**
     * Constructs a {@code Tags} instance by merging two sets of tags in time proportional
     * to the sum of their sizes.
     * @param other the set of tags to merge with this one.
     * @return a {@code Tags} instance with the merged sets of tags.
     */
    private KeyValues merged(KeyValues other) {
        if (other.length == 0) {
            return this;
        }
        if (Objects.equals(this, other)) {
            return this;
        }
        KeyValue[] sortedSet = new KeyValue[this.length + other.length];
        int sortedIdx = 0, thisIdx = 0, otherIdx = 0;
        while (thisIdx < this.length && otherIdx < other.length) {
            int cmp = this.sortedSet[thisIdx].compareTo(other.sortedSet[otherIdx]);
            if (cmp > 0) {
                sortedSet[sortedIdx] = other.sortedSet[otherIdx];
                otherIdx++;
            }
            else if (cmp < 0) {
                sortedSet[sortedIdx] = this.sortedSet[thisIdx];
                thisIdx++;
            }
            else {
                // In case of key conflict prefer tag from other set
                sortedSet[sortedIdx] = other.sortedSet[otherIdx];
                thisIdx++;
                otherIdx++;
            }
            sortedIdx++;
        }
        int thisRemaining = this.length - thisIdx;
        if (thisRemaining > 0) {
            System.arraycopy(this.sortedSet, thisIdx, sortedSet, sortedIdx, thisRemaining);
            sortedIdx += thisRemaining;
        }
        int otherRemaining = other.length - otherIdx;
        if (otherIdx < other.sortedSet.length) {
            System.arraycopy(other.sortedSet, otherIdx, sortedSet, sortedIdx, otherRemaining);
            sortedIdx += otherRemaining;
        }
        return new KeyValues(sortedSet, sortedIdx);
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
        return and(make(keyValues));
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
        if (keyValues == null || keyValues == EMPTY || !keyValues.iterator().hasNext()) {
            return this;
        }

        if (this.length == 0) {
            return KeyValues.of(keyValues);
        }

        return merged(KeyValues.of(keyValues));
    }

    @Override
    public Iterator<KeyValue> iterator() {
        return new ArrayIterator();
    }

    private class ArrayIterator implements Iterator<KeyValue> {

        private int currentIndex = 0;

        @Override
        public boolean hasNext() {
            return currentIndex < length;
        }

        @Override
        public KeyValue next() {
            return sortedSet[currentIndex++];
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("cannot remove items from key values");
        }

    }

    @Override
    public Spliterator<KeyValue> spliterator() {
        return Spliterators.spliterator(sortedSet, 0, length, Spliterator.IMMUTABLE | Spliterator.ORDERED
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
        for (int i = 0; i < length; i++) {
            result = 31 * result + sortedSet[i].hashCode();
        }
        return result;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return this == obj || (obj != null && getClass() == obj.getClass() && keyValuesEqual((KeyValues) obj));
    }

    private boolean keyValuesEqual(KeyValues obj) {
        if (sortedSet == obj.sortedSet)
            return true;

        if (length != obj.length)
            return false;

        for (int i = 0; i < length; i++) {
            if (!sortedSet[i].equals(obj.sortedSet[i]))
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
        if (keyValues == null || keyValues == EMPTY || !keyValues.iterator().hasNext()) {
            return KeyValues.empty();
        }
        else if (keyValues instanceof KeyValues) {
            return (KeyValues) keyValues;
        }
        else if (keyValues instanceof Collection) {
            Collection<? extends KeyValue> keyValuesCollection = (Collection<? extends KeyValue>) keyValues;
            return make(keyValuesCollection.toArray(new KeyValue[0]));
        }
        else {
            return make(StreamSupport.stream(keyValues.spliterator(), false).toArray(KeyValue[]::new));
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
        return new KeyValues(new KeyValue[] { KeyValue.of(key, value) }, 1);
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
        return make(keyValueArray);
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
