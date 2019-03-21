/**
 * Copyright 2017 Pivotal Software, Inc.
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

public class GraphiteHierarchicalNameMapper implements HierarchicalNameMapper {
    private final String[] tagsAsPrefix;

    public GraphiteHierarchicalNameMapper(String... tagsAsPrefix) {
        this.tagsAsPrefix = tagsAsPrefix;
    }

    @Override
    public String toHierarchicalName(Meter.Id id, NamingConvention convention) {
        StringBuilder prefix = new StringBuilder();
        StringBuilder tags = new StringBuilder();

        if (id.getTags().iterator().hasNext()) {
            conventionTags: for (Tag tag : id.getConventionTags(convention)) {
                for (String tagPrefix : tagsAsPrefix) {
                    if (tag.getKey().equals(tagPrefix)) {
                        prefix.append(tag.getValue()).append(".");
                        continue conventionTags;
                    }
                }

                tags.append(("." + tag.getKey() + "." + tag.getValue()).replace(" ", "_"));
            }
        }

        return prefix.toString() + id.getConventionName(convention) + tags;
    }
}
