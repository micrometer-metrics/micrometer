/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.graphite;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

/**
 * Tag based {@link HierarchicalNameMapper} for Graphite. This is a bit confusing, as this
 * is actually used for dimensional metrics via Graphite's tagging support, as opposed to
 * the {@link GraphiteHierarchicalNameMapper}
 *
 * @see <a href="https://graphite.readthedocs.io/en/latest/tags.html">Graphite Tag
 * Support</a>
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Andrew Fitzgerald
 * @since 1.4.0
 */
public class GraphiteDimensionalNameMapper implements HierarchicalNameMapper {

    @Override
    public String toHierarchicalName(Meter.Id id, NamingConvention convention) {
        StringBuilder hierarchicalName = new StringBuilder();
        hierarchicalName.append(id.getConventionName(convention));
        for (Tag tag : id.getTagsAsIterable()) {
            hierarchicalName.append(';')
                .append(convention.tagKey(tag.getKey()))
                .append('=')
                .append(convention.tagValue(tag.getValue()));
        }
        return hierarchicalName.toString();
    }

}
