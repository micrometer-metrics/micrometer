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
 * An immutable collection of {@link Tag Tags} that are guaranteed to be sorted and deduplicated by tag key.
 *
 * @author Jon Schneider
 * @author Maciej Walkowiak
 * @author Phillip Webb
 * @author Johnny Lim
 */
// TODO: Make final in 3.0.0 and remove bounds
@SuppressWarnings({"rawtypes", "unchecked"})
public class Tags<T extends Tag> implements Iterable<T> {

    private static final Tags EMPTY = new Tags(new Tag[]{});

    // Make this private
    protected final T[] tags;
    protected int last;

    // TODO: Make private in 3.0.0
    // DO NOT USE - IT'S FOR INTERNAL USE ONLY
    public Tags(T[] tags) {
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
     * Return a new {@code Tags} instance by merging this collection and the specified key/value pair.
     *
     * @param key   the tag key to add
     * @param value the tag value to add
     * @return a new {@code Tags} instance
     */
    public Tags and(String key, String value) {
        return and(tagOf(key, value));
    }

    protected T tagOf(String key, String value) {
        return (T) Tag.of(key, value);
    }

    protected Tags<T> tagsOf(String key, String value) {
        return Tags.of(key, value);
    }

    protected Tags<T> tagsOf(@Nullable String... keyValues) {
        return Tags.of(keyValues);
    }

    protected Tags<T> tagsOf(@Nullable Iterable<? extends T> tags) {
        return Tags.of(tags);
    }

    /**
     * Return a new {@code Tags} instance by merging this collection and the specified key/value pairs.
     *
     * @param keyValues the key/value pairs to add
     * @return a new {@code Tags} instance
     */
    public Tags and(@Nullable String... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return this;
        }
        return and(tagsOf(keyValues));
    }

    /**
     * Return a new {@code Tags} instance by merging this collection and the specified tags.
     *
     * @param tags the tags to add
     * @return a new {@code Tags} instance
     */
    public Tags and(@Nullable T... tags) {
        if (tags == null || tags.length == 0) {
            return this;
        }
        Tag[] newTags = new Tag[last + tags.length];
        System.arraycopy(this.tags, 0, newTags, 0, last);
        System.arraycopy(tags, 0, newTags, last, tags.length);
        return new Tags(newTags);
    }

    /**
     * Return a new {@code Tags} instance by merging this collection and the specified tags.
     *
     * @param tags the tags to add
     * @return a new {@code Tags} instance
     */
    public Tags and(@Nullable Iterable<? extends T> tags) {
        if (tags == null || !tags.iterator().hasNext()) {
            return this;
        }

        if (this.tags.length == 0) {
            return tagsOf(tags);
        }

        return and(tagsOf(tags).tags);
    }

    @Override
    public Iterator<T> iterator() {
        return new ArrayIterator();
    }

    private class ArrayIterator implements Iterator<T> {
        private int currentIndex = 0;

        @Override
        public boolean hasNext() {
            return currentIndex < last;
        }

        @Override
        public T next() {
            return tags[currentIndex++];
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("cannot remove items from tags");
        }
    }

    /**
     * Return a stream of the contained tags.
     *
     * @return a tags stream
     */
    public Stream<T> stream() {
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
        return this == obj || obj != null && getClass() == obj.getClass() && tagsEqual((Tags) obj);
    }

    private boolean tagsEqual(Tags obj) {
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
     * Return a new {@code Tags} instance by concatenating the specified collections of tags.
     *
     * @param tags      the first set of tags
     * @param otherTags the second set of tags
     * @return the merged tags
     */
    public static <T extends Tag> Tags concat(@Nullable Iterable<? extends T> tags, @Nullable Iterable<? extends T> otherTags) {
        return Tags.of(tags).and(otherTags);
    }

    /**
     * Return a new {@code Tags} instance by concatenating the specified tags and key/value pairs.
     *
     * @param tags      the first set of tags
     * @param keyValues the additional key/value pairs to add
     * @return the merged tags
     */
    public static <T extends Tag> Tags concat(@Nullable Iterable<? extends T> tags, @Nullable String... keyValues) {
        return Tags.of(tags).and(keyValues);
    }

    /**
     * Return a new {@code Tags} instance containing tags constructed from the specified source tags.
     *
     * @param tags the tags to add
     * @return a new {@code Tags} instance
     */
    public static <T extends Tag> Tags of(@Nullable Iterable<? extends T> tags) {
        if (tags == null || !tags.iterator().hasNext()) {
            return Tags.empty();
        } else if (tags instanceof Tags) {
            return (Tags) tags;
        } else if (tags instanceof Collection) {
            Collection<? extends Tag> tagsCollection = (Collection<? extends Tag>) tags;
            return new Tags(tagsCollection.toArray(new Tag[0]));
        } else {
            return new Tags(StreamSupport.stream(tags.spliterator(), false).toArray(Tag[]::new));
        }
    }

    /**
     * Return a new {@code Tags} instance containing tags constructed from the specified key/value pair.
     *
     * @param key   the tag key to add
     * @param value the tag value to add
     * @return a new {@code Tags} instance
     */
    public static Tags of(String key, String value) {
        return new Tags(new Tag[]{Tag.of(key, value)});
    }

    /**
     * Return a new {@code Tags} instance containing tags constructed from the specified key/value pairs.
     *
     * @param keyValues the key/value pairs to add
     * @return a new {@code Tags} instance
     */
    public static Tags of(@Nullable String... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return empty();
        }
        if (keyValues.length % 2 == 1) {
            throw new IllegalArgumentException("size must be even, it is a set of key=value pairs");
        }
        Tag[] tags = new Tag[keyValues.length / 2];
        for (int i = 0; i < keyValues.length; i += 2) {
            tags[i / 2] = Tag.of(keyValues[i], keyValues[i + 1]);
        }
        return new Tags(tags);
    }

    /**
     * Return a new {@code Tags} instance containing tags constructed from the specified tags.
     *
     * @param tags the tags to add
     * @return a new {@code Tags} instance
     */
    public static <T extends Tag> Tags of(@Nullable T... tags) {
        return empty().and(tags);
    }

    /**
     * Return a {@code Tags} instance that contains no elements.
     *
     * @return an empty {@code Tags} instance
     */
    public static Tags empty() {
        return EMPTY;
    }

    @Override
    public String toString() {
        return stream().map(Tag::toString).collect(joining(",", "[", "]"));
    }
}
