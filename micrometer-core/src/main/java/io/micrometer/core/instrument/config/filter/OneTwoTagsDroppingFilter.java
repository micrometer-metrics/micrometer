/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.core.instrument.config.filter;

import io.micrometer.common.lang.NonNull;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;

import java.util.*;

/**
 * Processes identifiers by dropping the tags with the matching keys. Separated from
 * {@link SetBackedTagDroppingFilter} for performance reasons: in a frequent case end user
 * needs to drop only one or two tags, it's much cheaper to abstain from more expensive
 * lookups.
 *
 * @see SetBackedTagDroppingFilter for input with larger cardinality.
 * @see NoOpFilter for input with the lowest cardinality.
 * @since 1.15
 */
public class OneTwoTagsDroppingFilter implements MeterFilter {

    @NonNull
    private final String first;

    @Nullable
    private final String second;

    private final int expectedSize;

    OneTwoTagsDroppingFilter(@NonNull String first, @Nullable String second, int expectedSize) {
        this.first = first;
        this.second = second;
        this.expectedSize = expectedSize;
    }

    @NonNull
    @Override
    public Meter.Id map(Meter.Id id) {
        Iterator<Tag> iterator = id.getTagsAsIterable().iterator();

        if (!iterator.hasNext()) {
            // fast path avoiding list allocation completely
            return id;
        }

        List<Tag> replacement = new ArrayList<>(expectedSize);
        int removals = 0;

        while (iterator.hasNext()) {
            Tag tag = iterator.next();
            String key = tag.getKey();

            if (removals != 2 && (key.equals(first) || key.equals(second))) {
                removals++;
                continue;
            }

            replacement.add(tag);
        }

        return removals == 0 ? id : id.replaceTags(replacement);
    }

    public static MeterFilter of(@NonNull String first, @Nullable String second, int expectedSize) {
        return new OneTwoTagsDroppingFilter(first, second, expectedSize);
    }

    public static MeterFilter of(@NonNull String first, @Nullable String second) {
        return of(first, second, FilterSupport.DEFAULT_TAG_COUNT_EXPECTATION);
    }

    public static MeterFilter of(@NonNull String first, int expectedSize) {
        return of(first, null, expectedSize);
    }

    public static MeterFilter of(@NonNull String first) {
        return of(first, null);
    }

    public static MeterFilter of(@NonNull Collection<String> keys, int expectedSize) {
        if (keys.size() != 1 && keys.size() != 2) {
            throw new IllegalArgumentException("Expected collection with exactly one or two elements, got " + keys);
        }

        Iterator<String> iterator = keys.iterator();
        String first = iterator.next();
        String second = iterator.hasNext() ? iterator.next() : null;

        return of(first, second, expectedSize);
    }

    public static MeterFilter of(@NonNull Collection<String> keys) {
        return of(keys, FilterSupport.DEFAULT_TAG_COUNT_EXPECTATION);
    }

    public static MeterFilter of(@NonNull String[] keys, int expectedSize) {
        return of(Arrays.asList(keys), expectedSize);
    }

    public static MeterFilter of(@NonNull String... keys) {
        return of(keys, FilterSupport.DEFAULT_TAG_COUNT_EXPECTATION);
    }

}
