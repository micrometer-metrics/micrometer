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
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;

import java.util.*;

/**
 * Processes identifiers by dropping the tags with the matching keys.
 *
 * @see OneTwoTagsDroppingFilter for input with lower cardinality.
 * @see NoOpFilter for input with the lowest cardinality.
 * @since 1.15
 */
public class SetBackedTagDroppingFilter implements MeterFilter {

    private final Set<String> keys;

    private final int expectedSize;

    SetBackedTagDroppingFilter(@NonNull Set<String> keys, int expectedSize) {
        this.keys = keys;
        this.expectedSize = expectedSize;
    }

    @Override
    public Meter.Id map(Meter.Id id) {
        Iterator<Tag> iterator = id.getTagsAsIterable().iterator();

        if (!iterator.hasNext()) {
            // fast path avoiding list allocation completely
            return id;
        }

        List<Tag> replacement = new ArrayList<>(expectedSize);
        boolean intercepted = false;

        while (iterator.hasNext()) {
            Tag tag = iterator.next();

            if (keys.contains(tag.getKey())) {
                intercepted = true;
                continue;
            }

            replacement.add(tag);
        }

        if (!intercepted) {
            // Nothing has changed? Return as is and let GC do the easy
            // job of marking zero references array list as trash.
            return id;
        }

        if (replacement.isEmpty()) {
            // At the moment of writing replaceTags(List) would invoke
            // a bit heavier path, so it's better to provide empty tags
            // directly
            return id.replaceTags(Tags.empty());
        }

        return id.replaceTags(replacement);
    }

    public static MeterFilter of(@NonNull Set<String> keys, int expectedSize) {
        return new SetBackedTagDroppingFilter(keys, expectedSize);
    }

    public static MeterFilter of(@NonNull Set<String> keys) {
        return of(keys, FilterSupport.DEFAULT_TAG_COUNT_EXPECTATION);
    }

    public static MeterFilter of(@NonNull Collection<String> keys, int expectedSize) {
        Set<String> converted = keys instanceof Set ? (Set<String>) keys : new HashSet<>(keys);
        return of(converted, expectedSize);
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
