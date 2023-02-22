/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument.util;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;

import java.util.stream.Collectors;

/**
 * Defines the mapping between a combination of name + dimensional tags and a hierarchical
 * name.
 *
 * @author Jon Schneider
 */
public interface HierarchicalNameMapper {

    /**
     * Sort tags alphabetically by key and append tag key values to the name with '.',
     * e.g. {@code http_server_requests.response.200.method.GET}
     */
    HierarchicalNameMapper DEFAULT = (id,
            convention) -> id.getConventionName(convention) + id.getConventionTags(convention)
                .stream()
                .map(t -> "." + t.getKey() + "." + t.getValue())
                .map(nameSegment -> nameSegment.replace(" ", "_"))
                .collect(Collectors.joining(""));

    String toHierarchicalName(Meter.Id id, NamingConvention convention);

}
