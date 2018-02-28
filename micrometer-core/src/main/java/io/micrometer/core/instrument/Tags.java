/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument;

import io.micrometer.core.lang.Nullable;

import java.util.*;
import java.util.stream.Stream;

/**
 * An immutable collection of {@link Tag Tags}.
 *
 * @author Jon Schneider
 * @author Maciej Walkowiak
 * @author Phillip Webb
 */
public final class Tags implements Iterable<Tag> {

    private static final Tags EMPTY = new Tags(Collections.emptyMap());

    private final Map<String, Tag> tags;

    private Tags(Map<String, Tag> tags) {
        this.tags = Collections.unmodifiableMap(tags);
    }

    /**
     * Return a new {@link Tags} instance by merging this collection and the specific key/value pair.
     *
     * @param key   the tag key to add
     * @param value the tag value to add
     * @return a new {@link Tags} instance
     */
    public Tags and(String key, String value) {
        return and(Tag.of(key, value));
    }

    /**
     * Return a new {@link Tags} instance by merging this collection and the specific key/value pairs.
     *
     * @param keyValues the key value pairs to add
     * @return a new {@link Tags} instance
     */
    public Tags and(@Nullable String... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return this;
        }
        if (keyValues.length % 2 == 1) {
            throw new IllegalArgumentException("size must be even, it is a set of key=value pairs");
        }
        List<Tag> tags = new ArrayList<>(keyValues.length / 2);
        for (int i = 0; i < keyValues.length; i += 2) {
            tags.add(Tag.of(keyValues[i], keyValues[i + 1]));
        }
        return and(tags);
    }

    /**
     * Return a new {@link Tags} instance by merging this collection and the specific tags.
     *
     * @param tags the tags to add
     * @return a new {@link Tags} instance
     */
    public Tags and(@Nullable Tag... tags) {
        if (tags == null || tags.length == 0) {
            return this;
        }
        return and(Arrays.asList(tags));
    }

    /**
     * Return a new {@link Tags} instance by merging this collection and the specific tags.
     *
     * @param tags the tags to add
     * @return a new {@link Tags} instance
     */
    public Tags and(@Nullable Iterable<? extends Tag> tags) {
        if (tags == null || !tags.iterator().hasNext()) {
            return this;
        }
        Map<String, Tag> merged = new LinkedHashMap<>(this.tags);
        tags.forEach(tag -> merged.put(tag.getKey(), tag));
        return new Tags(merged);
    }

    @Override
    public Iterator<Tag> iterator() {
        return tags.values().iterator();
    }

    /**
     * Return a stream of the contained tags.
     *
     * @return a tags stream
     */
    public Stream<Tag> stream() {
        return tags.values().stream();
    }

    @Override
    public int hashCode() {
        return tags.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return this == obj || obj != null && getClass() == obj.getClass() && tags.equals(((Tags) obj).tags);
    }

    /**
     * Return a new {@link Tags} instance my concatenating the specified values.
     *
     * @param tags      the first set of tags
     * @param otherTags the second set of tags
     * @return the merged tags
     */
    public static Tags concat(Iterable<? extends Tag> tags, Iterable<Tag> otherTags) {
        return Tags.of(tags).and(otherTags);
    }

    /**
     * Return a new {@link Tags} instance my concatenating the specified key value pairs.
     *
     * @param tags      the first set of tags
     * @param keyValues the additional key value pairs to add
     * @return the merged tags
     */
    public static Tags concat(Iterable<? extends Tag> tags, String... keyValues) {
        return Tags.of(tags).and(keyValues);
    }

    /**
     * Return a new {@link Tags} instance containing tags constructed from the specified source tags.
     *
     * @param tags the tags to add
     * @return a new {@link Tags} instance
     */
    public static Tags of(Iterable<? extends Tag> tags) {
        if (tags instanceof Tags) {
            return (Tags) tags;
        }
        return empty().and(tags);
    }

    /**
     * Return a new {@link Tags} instance containing tags constructed from the specified key value pair.
     *
     * @param key   the tag key to add
     * @param value the tag value to add
     * @return a new {@link Tags} instance
     */
    public static Tags of(String key, String value) {
        return empty().and(key, value);
    }

    /**
     * Return a new {@link Tags} instance containing tags constructed from the specified key value pairs.
     *
     * @param keyValues the key value pairs to add
     * @return a new {@link Tags} instance
     */
    public static Tags of(String... keyValues) {
        return empty().and(keyValues);
    }

    /**
     * Return a new {@link Tags} instance containing tags constructed from the specified tags.
     *
     * @param tags the tags to add
     * @return a new {@link Tags} instance
     */
    public static Tags of(Tag... tags) {
        return empty().and(tags);
    }

    /**
     * Return a {@link Tags} instance that contains no elements.
     *
     * @return an empty {@link Tags} instance
     */
    public static Tags empty() {
        return EMPTY;
    }
}
