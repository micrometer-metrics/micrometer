/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.metrics.instrument.internal;

import org.springframework.metrics.instrument.Measurement;
import org.springframework.metrics.instrument.Tag;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Stream.concat;
import static java.util.stream.StreamSupport.stream;

public class MeterId {
    private final String name;
    private final List<Tag> tags;

    public MeterId(String name, Iterable<Tag> tags) {
        this.name = name;
        this.tags = stream(tags.spliterator(), false).sorted(Comparator.comparing(Tag::getKey))
                .collect(Collectors.toList());
    }

    public MeterId(String name, Tag... tags) {
        this(name, Arrays.asList(tags));
    }

    public String getName() {
        return name;
    }

    public List<Tag> getTags() {
        return tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MeterId meterId = (MeterId) o;
        return name.equals(meterId.name) && tags.equals(meterId.tags);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + tags.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "MeterId{" +
                "name='" + name + '\'' +
                ", tags=" + tags +
                '}';
    }

    /**
     * @return A new id with additional tags.
     */
    public MeterId withTags(Tag... extraTags) {
        return new MeterId(name, concat(tags.stream(), Stream.of(extraTags)).collect(Collectors.toList()));
    }

    /**
     * @return A new id with additional tags.
     */
    public MeterId withTags(Iterable<Tag> extraTags) {
        return new MeterId(name, concat(tags.stream(), stream(extraTags.spliterator(), false)).collect(Collectors.toList()));
    }

    public Measurement measurement(double value) {
        return new Measurement(name, tags, value);
    }
}