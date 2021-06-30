/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.graphite;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

import java.util.Arrays;
import java.util.List;

/**
 * {@link HierarchicalNameMapper} for Graphite.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @since 1.4.0
 */
public class GraphiteHierarchicalNameMapper implements HierarchicalNameMapper {
    private final List<String> tagsAsPrefix;

    public GraphiteHierarchicalNameMapper(String... tagsAsPrefix) {
        this.tagsAsPrefix = Arrays.asList(tagsAsPrefix);
    }

    @Override
    public String toHierarchicalName(Meter.Id id, NamingConvention convention) {
        StringBuilder hierarchicalName = new StringBuilder();
        for (String tagKey : tagsAsPrefix) {
            String tagValue = id.getTag(tagKey);
            if (tagValue != null) {
                hierarchicalName.append(convention.tagValue(tagValue)).append('.');
            }
        }
        hierarchicalName.append(id.getConventionName(convention));
        for (Tag tag : id.getTagsAsIterable()) {
            if (!tagsAsPrefix.contains(tag.getKey())) {
                hierarchicalName.append('.').append(convention.tagKey(tag.getKey()))
                        .append('.').append(convention.tagValue(tag.getValue()));
            }
        }
        return hierarchicalName.toString();
    }

}

