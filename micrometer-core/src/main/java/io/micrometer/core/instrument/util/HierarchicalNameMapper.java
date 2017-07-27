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
package io.micrometer.core.instrument.util;

import io.micrometer.core.instrument.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Defines the mapping between a combination of name + dimensional tags and a hierarchical name.
 *
 * @author Jon Schneider
 */
public class HierarchicalNameMapper {
    public static final HierarchicalNameMapper DEFAULT = new HierarchicalNameMapper();

    protected String tagSeparator = ".";
    protected String valueSeparator = ".";
    protected Comparator<Tag> tagComparator = Comparator.comparing(Tag::getKey);

    /**
     * The separator between two tags.
     */
    public HierarchicalNameMapper setTagSeparator(String tagSeparator) {
        this.tagSeparator = tagSeparator;
        return this;
    }

    /**
     * The separator between a tag key and its value.
     */
    public HierarchicalNameMapper setValueSeparator(String valueSeparator) {
        this.valueSeparator = valueSeparator;
        return this;
    }

    public HierarchicalNameMapper setTagComparator(Comparator<Tag> tagComparator) {
        this.tagComparator = tagComparator;
        return this;
    }

    public String toHierarchicalName(String name, Collection<Tag> tags) {
        List<Tag> tagsCopy = new ArrayList<>(tags);
        tagsCopy.sort(tagComparator);
        return name + tagSeparator + tagsCopy.stream()
                .map(t -> t.getKey() + valueSeparator + t.getValue())
                .collect(Collectors.joining(tagSeparator));
    }
}
