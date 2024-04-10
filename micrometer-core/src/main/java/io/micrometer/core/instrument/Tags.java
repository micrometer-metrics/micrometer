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
package io.micrometer.core.instrument;

import io.micrometer.common.lang.Nullable;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.joining;

/**
 * An immutable collection of {@link Tag Tags} that are guaranteed to be sorted and
 * deduplicated by tag key.
 *
 * @author Jon Schneider
 * @author Maciej Walkowiak
 * @author Phillip Webb
 * @author Johnny Lim
 */
public final class Tags implements Iterable<Tag> {

    private static final Tags EMPTY = new Tags(new Tag[] {}, 0);

    private final Tag[] sortedSet;

    private final int length;

    private Tags(Tag[] sortedSet, int length) {
        assert isSortedSet(sortedSet, length) : "bug on caller side: construction invariant violated";
        this.sortedSet = sortedSet;
        this.length = length;
    }

    private static boolean isSortedSet(Tag[] sortedSet, int length) {
        if (length > sortedSet.length) {
            return false;
        }
        for (int i = 0; i < length - 1; i++) {
            int cmp = sortedSet[i].compareTo(sortedSet[i + 1]);
            if (cmp >= 0) {
                return false;
            }
        }
        return true;
    }

    private static Tags make(Tag[] tags) {
        Arrays.sort(tags);
        int len = dedup(tags);
        return new Tags(tags, len);
    }

    private static int dedup(Tag[] tags) {
        int n = tags.length;

        if (n == 0 || n == 1) {
            return n;
        }

        // index of next unique element
        int j = 0;

        for (int i = 0; i < n - 1; i++)
            if (!tags[i].getKey().equals(tags[i + 1].getKey()))
                tags[j++] = tags[i];

        tags[j++] = tags[n - 1];
        return j;
    }

    private Tags merged(Tags other) {
        if (other.length == 0) {
            return this;
        }
        if (Objects.equals(this, other)) {
            return this;
        }
        Tag[] sortedSet = new Tag[this.length + other.length];
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
        return new Tags(sortedSet, sortedIdx);
    }

    /**
     * Return a new {@code Tags} instance by merging this collection and the specified
     * key/value pair.
     * @param key the tag key to add
     * @param value the tag value to add
     * @return a new {@code Tags} instance
     */
    public Tags and(String key, String value) {
        return and(Tag.of(key, value));
    }

    /**
     * Return a new {@code Tags} instance by merging this collection and the specified
     * key/value pairs.
     * @param keyValues the key/value pairs to add, elements mustn't be null
     * @return a new {@code Tags} instance
     */
    public Tags and(@Nullable String... keyValues) {
        if (blankVarargs(keyValues)) {
            return this;
        }
        return and(Tags.of(keyValues));
    }

    /**
     * Return a new {@code Tags} instance by merging this collection and the specified
     * tags.
     * @param tags the tags to add, elements mustn't be null
     * @return a new {@code Tags} instance
     */
    public Tags and(@Nullable Tag... tags) {
        if (blankVarargs(tags)) {
            return this;
        }
        return and(make(tags));
    }

    /**
     * Return a new {@code Tags} instance by merging this collection and the specified
     * tags.
     * @param tags the tags to add, elements mustn't be null
     * @return a new {@code Tags} instance
     */
    public Tags and(@Nullable Iterable<? extends Tag> tags) {
        if (tags == null || tags == EMPTY || !tags.iterator().hasNext()) {
            return this;
        }

        if (this.length == 0) {
            return Tags.of(tags);
        }
        return merged(Tags.of(tags));
    }

    @Override
    public Iterator<Tag> iterator() {
        return new ArrayIterator();
    }

    private class ArrayIterator implements Iterator<Tag> {

        private int currentIndex = 0;

        @Override
        public boolean hasNext() {
            return currentIndex < length;
        }

        @Override
        public Tag next() {
            return sortedSet[currentIndex++];
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("cannot remove items from tags");
        }

    }

    @Override
    public Spliterator<Tag> spliterator() {
        return Spliterators.spliterator(sortedSet, 0, length, Spliterator.IMMUTABLE | Spliterator.ORDERED
                | Spliterator.DISTINCT | Spliterator.NONNULL | Spliterator.SORTED);
    }

    /**
     * Return a stream of the contained tags.
     * @return a tags stream
     */
    public Stream<Tag> stream() {
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
        return this == obj || obj != null && getClass() == obj.getClass() && tagsEqual((Tags) obj);
    }

    private boolean tagsEqual(Tags obj) {
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
     * Return a new {@code Tags} instance by concatenating the specified collections of
     * tags.
     * @param tags the first set of tags, elements mustn't be null
     * @param otherTags the second set of tags, elements mustn't be null
     * @return the merged tags
     */
    public static Tags concat(@Nullable Iterable<? extends Tag> tags, @Nullable Iterable<? extends Tag> otherTags) {
        return Tags.of(tags).and(otherTags);
    }

    /**
     * Return a new {@code Tags} instance by concatenating the specified tags and
     * key/value pairs.
     * @param tags the first set of tags, elements mustn't be null
     * @param keyValues the additional key/value pairs to add, elements mustn't be null
     * @return the merged tags
     */
    public static Tags concat(@Nullable Iterable<? extends Tag> tags, @Nullable String... keyValues) {
        return Tags.of(tags).and(keyValues);
    }

    /**
     * Return a new {@code Tags} instance containing tags constructed from the specified
     * source tags.
     * @param tags the tags to add, elements mustn't be null
     * @return a new {@code Tags} instance
     */
    public static Tags of(@Nullable Iterable<? extends Tag> tags) {
        if (tags == null || tags == EMPTY || !tags.iterator().hasNext()) {
            return Tags.empty();
        }
        else if (tags instanceof Tags) {
            return (Tags) tags;
        }
        else if (tags instanceof Collection) {
            Collection<? extends Tag> tagsCollection = (Collection<? extends Tag>) tags;
            return make(tagsCollection.toArray(new Tag[0]));
        }
        else {
            return make(StreamSupport.stream(tags.spliterator(), false).toArray(Tag[]::new));
        }
    }

    /**
     * Return a new {@code Tags} instance containing tags constructed from the specified
     * key/value pair.
     * @param key the tag key to add
     * @param value the tag value to add
     * @return a new {@code Tags} instance
     */
    public static Tags of(String key, String value) {
        return new Tags(new Tag[] { Tag.of(key, value) }, 1);
    }

    /**
     * Return a new {@code Tags} instance containing tags constructed from the specified
     * key/value pairs.
     * @param keyValues the key/value pairs to add, elements mustn't be null
     * @return a new {@code Tags} instance
     */
    public static Tags of(@Nullable String... keyValues) {
        if (blankVarargs(keyValues)) {
            return empty();
        }
        if (keyValues.length % 2 == 1) {
            throw new IllegalArgumentException("size must be even, it is a set of key=value pairs");
        }
        Tag[] tags = new Tag[keyValues.length / 2];
        for (int i = 0; i < keyValues.length; i += 2) {
            tags[i / 2] = Tag.of(keyValues[i], keyValues[i + 1]);
        }
        return make(tags);
    }

    private static boolean blankVarargs(@Nullable Object[] args) {
        return args == null || args.length == 0 || (args.length == 1 && args[0] == null);
    }

    /**
     * Return a new {@code Tags} instance containing tags constructed from the specified
     * tags.
     * @param tags the tags to add, elements mustn't be null
     * @return a new {@code Tags} instance
     */
    public static Tags of(@Nullable Tag... tags) {
        return empty().and(tags);
    }

    /**
     * Return a {@code Tags} instance that contains no elements.
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
