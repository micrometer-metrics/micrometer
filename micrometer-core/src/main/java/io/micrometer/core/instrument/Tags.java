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

    private static final Tag[] EMPTY_TAG_ARRAY = new Tag[0];

    private static final Tags EMPTY = new Tags(EMPTY_TAG_ARRAY, 0);

    /**
     * An array of {@code Tag} objects containing the sorted and deduplicated tags.
     */
    private final Tag[] sortedSet;

    /**
     * The number of valid tags present in the {@link #sortedSet} array.
     */
    private final int length;

    /**
     * A constructor that initializes a {@code Tags} object with a sorted set of tags and
     * its length.
     * @param sortedSet an ordered set of unique tags by key
     * @param length the number of valid tags in the {@code sortedSet}
     */
    private Tags(Tag[] sortedSet, int length) {
        this.sortedSet = sortedSet;
        this.length = length;
    }

    /**
     * Checks if the first {@code length} elements of the {@code tags} array form an
     * ordered set of tags.
     * @param tags an array of tags.
     * @param length the number of elements to check.
     * @return {@code true} if the first {@code length} elements of {@code tags} form an
     * ordered set; otherwise {@code false}.
     */
    private static boolean isSortedSet(Tag[] tags, int length) {
        if (length > tags.length) {
            return false;
        }

        // This style is intentionally chosen to have only one array
        // access per iteration (tags[i].compareTo(tags[i + 1]) would
        // have two).
        Tag current = tags[0];
        Tag next;

        for (int i = 0; i < length - 1; i++) {
            next = tags[i + 1];
            int cmp = current.compareTo(next);
            if (cmp >= 0) {
                return false;
            }
            current = next;
        }
        return true;
    }

    /**
     * Constructs a {@code Tags} collection from the provided array of tags.
     * @param tags an array of {@code Tag} objects, possibly unordered and/or containing
     * duplicates.
     * @return a {@code Tags} instance with a deduplicated and ordered set of tags.
     */
    private static Tags toTags(Tag[] tags) {
        int len = tags.length;
        if (!isSortedSet(tags, len)) {
            Arrays.sort(tags);
            len = dedup(tags);
        }
        return new Tags(tags, len);
    }

    /**
     * Removes duplicate tags from an ordered array of tags. In the case of several
     * consecutive tags with the same key, only the last one is preserved.
     * @param tags an ordered array of {@code Tag} objects.
     * @return the number of unique tags in the {@code tags} array after removing
     * duplicates.
     */
    private static int dedup(Tag[] tags) {
        int n = tags.length;

        if (n == 0 || n == 1) {
            return n;
        }

        // index of next unique element
        int j = 0;

        // The following is intentionally written in this style to facilitate performance.
        // Normally one would just do tags[i].getKey().equals(tags[i + 1].getKey()),
        // but the tags[i + 1].getKey() value is exactly the same as tags[i].getKey()
        // for the next iteration, thus caching it in a variable halves the number
        // of lookups.
        // The compiler is very unlikely to do this for us because of the absence of
        // "this data is immutable" signs, even if MM doesn't enforce any HB
        // relations with external modifications in this case. You can run the
        // associated benchmarks to check the behavior for your specific JVM.
        String current = tags[0].getKey();
        String next;

        for (int i = 0; i < n - 1; i++) {
            next = tags[i + 1].getKey();
            if (!current.equals(next))
                tags[j++] = tags[i];
            current = next;
        }

        tags[j++] = tags[n - 1];
        return j;
    }

    /**
     * Constructs a {@code Tags} instance by merging two sets of tags in time proportional
     * to the sum of their sizes.
     * @param other the set of tags to merge with this one.
     * @return a {@code Tags} instance with the merged sets of tags.
     */
    private Tags merge(Tags other) {
        if (length == 0) {
            return other;
        }

        if (other.length == 0 || tagsEqual(other)) {
            return this;
        }

        Tag[] sortedSet = new Tag[this.length + other.length];
        int sortedIndex = 0;
        int thisIndex = 0;
        int otherIndex = 0;
        while (thisIndex < this.length && otherIndex < other.length) {
            Tag thisTag = this.sortedSet[thisIndex];
            Tag otherTag = other.sortedSet[otherIndex];
            int cmp = thisTag.compareTo(otherTag);
            if (cmp > 0) {
                sortedSet[sortedIndex] = otherTag;
                otherIndex++;
            }
            else if (cmp < 0) {
                sortedSet[sortedIndex] = thisTag;
                thisIndex++;
            }
            else {
                // In case of key conflict prefer tag from other set
                sortedSet[sortedIndex] = otherTag;
                thisIndex++;
                otherIndex++;
            }
            sortedIndex++;
        }
        int thisRemaining = this.length - thisIndex;
        if (thisRemaining > 0) {
            System.arraycopy(this.sortedSet, thisIndex, sortedSet, sortedIndex, thisRemaining);
            sortedIndex += thisRemaining;
        }
        else {
            int otherRemaining = other.length - otherIndex;
            if (otherRemaining > 0) {
                System.arraycopy(other.sortedSet, otherIndex, sortedSet, sortedIndex, otherRemaining);
                sortedIndex += otherRemaining;
            }
        }
        return new Tags(sortedSet, sortedIndex);
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
        return and(toTags(tags));
    }

    /**
     * Return a new {@code Tags} instance by merging this collection and the specified
     * tags.
     * @param tags the tags to add, elements mustn't be null
     * @return a new {@code Tags} instance
     */
    public Tags and(@Nullable Iterable<? extends Tag> tags) {
        if (this.length == 0) {
            return Tags.of(tags);
        }

        // Tags.of() will take care of nulls, empty iterables and so on
        // merge() then will check if the argument is empty and reduce to no-op
        return merge(Tags.of(tags));
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
        return this == obj || (obj != null && getClass() == obj.getClass() && tagsEqual((Tags) obj));
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
        if (tags instanceof Tags) {
            return (Tags) tags;
        }
        else if (tags instanceof Collection) {
            Collection<? extends Tag> tagsCollection = (Collection<? extends Tag>) tags;
            if (tagsCollection.isEmpty()) {
                return Tags.empty();
            }
            return toTags(tagsCollection.toArray(EMPTY_TAG_ARRAY));
        }
        else if (emptyIterable(tags)) {
            return Tags.empty();
        }
        else {
            return toTags(StreamSupport.stream(tags.spliterator(), false).toArray(Tag[]::new));
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
        return of(Tag.of(key, value));
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
        return toTags(tags);
    }

    private static boolean blankVarargs(@Nullable Object[] args) {
        return args == null || args.length == 0 || (args.length == 1 && args[0] == null);
    }

    private static boolean emptyIterable(@Nullable Iterable<? extends Tag> iterable) {
        // Doing the checks in the ascending cost order
        if (iterable == null || iterable == EMPTY) {
            return true;
        }

        if (iterable instanceof Tags) {
            return ((Tags) iterable).length == 0;
        }

        if (iterable instanceof Collection) {
            return ((Collection<?>) iterable).isEmpty();
        }

        // While the compiler can theoretically avoid Iterator allocation here
        // (via scalarization), it is not guaranteed, thus leaving this check as the last
        // one
        return !iterable.iterator().hasNext();
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
