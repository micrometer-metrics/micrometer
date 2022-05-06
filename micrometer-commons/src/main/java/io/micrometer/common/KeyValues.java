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

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.micrometer.common.lang.Nullable;

import static java.util.stream.Collectors.joining;

/**
 * An immutable collection of {@link KeyValue Tags} that are guaranteed to be sorted and
 * deduplicated by tag key.
 *
 * @author Jon Schneider
 * @author Maciej Walkowiak
 * @author Phillip Webb
 * @author Johnny Lim
 * @since 1.10.0
 */
public final class KeyValues implements Iterable<KeyValue> {

    private static final KeyValues EMPTY = new KeyValues(new KeyValue[] {});

    private final KeyValue[] tags;

    private int last;

    private KeyValues(KeyValue[] tags) {
        this.tags = tags;
        Arrays.sort(this.tags);
        dedup();
    }

    private void dedup() {
        int n = tags.length;

        if (n == 0 || n == 1) {
            last = n;
            return;
        }

        // index of next unique element
        int j = 0;

        for (int i = 0; i < n - 1; i++)
            if (!tags[i].getKey().equals(tags[i + 1].getKey()))
                tags[j++] = tags[i];

        tags[j++] = tags[n - 1];
        last = j;
    }

    /**
     * Return a new {@code Tags} instance by merging this collection and the specified
     * key/value pair.
     * @param key the tag key to add
     * @param value the tag value to add
     * @return a new {@code Tags} instance
     */
    public KeyValues and(String key, String value) {
        return and(KeyValue.of(key, value));
    }

    /**
     * Return a new {@code Tags} instance by merging this collection and the specified
     * key/value pairs.
     * @param keyValues the key/value pairs to add
     * @return a new {@code Tags} instance
     */
    public KeyValues and(@Nullable String... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return this;
        }
        return and(KeyValues.of(keyValues));
    }

    /**
     * Return a new {@code Tags} instance by merging this collection and the specified
     * tags.
     * @param tags the tags to add
     * @return a new {@code Tags} instance
     */
    public KeyValues and(@Nullable KeyValue... tags) {
        if (tags == null || tags.length == 0) {
            return this;
        }
        KeyValue[] newTags = new KeyValue[last + tags.length];
        System.arraycopy(this.tags, 0, newTags, 0, last);
        System.arraycopy(tags, 0, newTags, last, tags.length);
        return new KeyValues(newTags);
    }

    /**
     * Return a new {@code Tags} instance by merging this collection and the specified
     * tags.
     * @param tags the tags to add
     * @return a new {@code Tags} instance
     */
    public KeyValues and(@Nullable Iterable<? extends KeyValue> tags) {
        if (tags == null || !tags.iterator().hasNext()) {
            return this;
        }

        if (this.tags.length == 0) {
            return KeyValues.of(tags);
        }

        return and(KeyValues.of(tags).tags);
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
            return tags[currentIndex++];
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("cannot remove items from tags");
        }

    }

    /**
     * Return a stream of the contained tags.
     * @return a tags stream
     */
    public Stream<KeyValue> stream() {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator(),
                Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.NONNULL | Spliterator.SORTED), false);
    }

    @Override
    public int hashCode() {
        int result = 1;
        for (int i = 0; i < last; i++) {
            result = 31 * result + tags[i].hashCode();
        }
        return result;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return this == obj || obj != null && getClass() == obj.getClass() && tagsEqual((KeyValues) obj);
    }

    private boolean tagsEqual(KeyValues obj) {
        if (tags == obj.tags)
            return true;

        if (last != obj.last)
            return false;

        for (int i = 0; i < last; i++) {
            if (!tags[i].equals(obj.tags[i]))
                return false;
        }

        return true;
    }

    /**
     * Return a new {@code Tags} instance by concatenating the specified collections of
     * tags.
     * @param tags the first set of tags
     * @param otherTags the second set of tags
     * @return the merged tags
     */
    public static KeyValues concat(@Nullable Iterable<? extends KeyValue> tags,
            @Nullable Iterable<? extends KeyValue> otherTags) {
        return KeyValues.of(tags).and(otherTags);
    }

    /**
     * Return a new {@code Tags} instance by concatenating the specified tags and
     * key/value pairs.
     * @param tags the first set of tags
     * @param keyValues the additional key/value pairs to add
     * @return the merged tags
     */
    public static KeyValues concat(@Nullable Iterable<? extends KeyValue> tags, @Nullable String... keyValues) {
        return KeyValues.of(tags).and(keyValues);
    }

    /**
     * Return a new {@code Tags} instance containing tags constructed from the specified
     * source tags.
     * @param tags the tags to add
     * @return a new {@code Tags} instance
     */
    public static KeyValues of(@Nullable Iterable<? extends KeyValue> tags) {
        if (tags == null || !tags.iterator().hasNext()) {
            return KeyValues.empty();
        }
        else if (tags instanceof KeyValues) {
            return (KeyValues) tags;
        }
        else if (tags instanceof Collection) {
            Collection<? extends KeyValue> tagsCollection = (Collection<? extends KeyValue>) tags;
            return new KeyValues(tagsCollection.toArray(new KeyValue[0]));
        }
        else {
            return new KeyValues(StreamSupport.stream(tags.spliterator(), false).toArray(KeyValue[]::new));
        }
    }

    /**
     * Return a new {@code Tags} instance containing tags constructed from the specified
     * key/value pair.
     * @param key the tag key to add
     * @param value the tag value to add
     * @return a new {@code Tags} instance
     */
    public static KeyValues of(String key, String value) {
        return new KeyValues(new KeyValue[] { KeyValue.of(key, value) });
    }

    /**
     * Return a new {@code Tags} instance containing tags constructed from the specified
     * key/value pairs.
     * @param keyValues the key/value pairs to add
     * @return a new {@code Tags} instance
     */
    public static KeyValues of(@Nullable String... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return empty();
        }
        if (keyValues.length % 2 == 1) {
            throw new IllegalArgumentException("size must be even, it is a set of key=value pairs");
        }
        KeyValue[] tags = new KeyValue[keyValues.length / 2];
        for (int i = 0; i < keyValues.length; i += 2) {
            tags[i / 2] = KeyValue.of(keyValues[i], keyValues[i + 1]);
        }
        return new KeyValues(tags);
    }

    /**
     * Return a new {@code Tags} instance containing tags constructed from the specified
     * tags.
     * @param tags the tags to add
     * @return a new {@code Tags} instance
     */
    public static KeyValues of(@Nullable KeyValue... tags) {
        return empty().and(tags);
    }

    /**
     * Return a {@code Tags} instance that contains no elements.
     * @return an empty {@code Tags} instance
     */
    public static KeyValues empty() {
        return EMPTY;
    }

    @Override
    public String toString() {
        return stream().map(KeyValue::toString).collect(joining(",", "[", "]"));
    }

}
