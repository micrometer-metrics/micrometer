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
import io.micrometer.core.instrument.config.MeterFilter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TagRenamingFilter implements MeterFilter {

    private final String meterNamePrefix;

    private final String matcher;

    private final String replacement;

    TagRenamingFilter(String meterNamePrefix, String matcher, String replacement) {
        this.meterNamePrefix = meterNamePrefix;
        this.matcher = matcher;
        this.replacement = replacement;
    }

    @NonNull
    @Override
    public Meter.Id map(@NonNull Meter.Id id) {
        if (!id.getName().startsWith(meterNamePrefix)) {
            return id;
        }

        Iterable<Tag> source = id.getTagsAsIterable();
        Iterator<Tag> iterator = source.iterator();

        if (!iterator.hasNext()) {
            // fast path avoiding list allocation completely
            return id;
        }

        int target = -1;
        int count = 0;

        while (iterator.hasNext()) {
            Tag tag = iterator.next();
            if (target == -1 && tag.getKey().equals(matcher)) {
                target = count;
            }
            count++;
        }

        if (target == -1) {
            return id;
        }

        List<Tag> processed = new ArrayList<>(count);
        int index = 0;

        for (Tag tag : id.getTags()) {
            Tag inserted = index == target ? Tag.of(replacement, tag.getValue()) : tag;
            processed.add(inserted);
            index++;
        }

        return id.replaceTags(processed);
    }

    public static MeterFilter of(String meterNamePrefix, String key, String replacement) {
        return new TagRenamingFilter(meterNamePrefix, key, replacement);
    }

}
