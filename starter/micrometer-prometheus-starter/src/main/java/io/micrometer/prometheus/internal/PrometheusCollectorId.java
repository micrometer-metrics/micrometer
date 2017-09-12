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
package io.micrometer.prometheus.internal;

import java.util.List;

public class PrometheusCollectorId {
    private final String name;
    private final List<String> tagKeys;
    private final String description;

    public PrometheusCollectorId(String name, List<String> tagKeys, String description) {
        this.name = name;
        this.tagKeys = tagKeys;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public List<String> getTagKeys() {
        return tagKeys;
    }

    public String getDescription() {
        return description == null ? " " : description;
    }
}
