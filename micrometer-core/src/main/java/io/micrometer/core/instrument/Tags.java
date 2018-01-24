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


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.StreamSupport.stream;

/**
 * @author Jon Schneider
 * @author Maciej Walkowiak
 */
public final class Tags {
    private Tags() {
    }

    public static Iterable<Tag> zip(String... keyValues) {
        if (keyValues.length % 2 == 1) {
            throw new IllegalArgumentException("size must be even, it is a set of key=value pairs");
        }

        Map<String, Tag> ts = new HashMap<>(keyValues.length / 2);
        for (int i = 0; i < keyValues.length; i += 2) {
            ts.put(keyValues[i], Tag.of(keyValues[i], keyValues[i + 1]));
        }

        return ts.values();
    }

    public static Iterable<Tag> concat(Iterable<Tag> tags, Iterable<Tag> otherTags) {
        if (!otherTags.iterator().hasNext())
            return tags;

        return Stream.concat(stream(tags.spliterator(), false), stream(otherTags.spliterator(), false))
            .collect(toMap(Tag::getKey, Function.identity(), (tag, otherTag) -> otherTag))
            .values();
    }

    public static Iterable<Tag> concat(Iterable<Tag> tags, String... keyValues) {
        return concat(tags, zip(keyValues));
    }

    public static Iterable<Tag> of(String tagKey, String tagValue) {
        return Collections.singletonList(Tag.of(tagKey, tagValue));
    }
}
