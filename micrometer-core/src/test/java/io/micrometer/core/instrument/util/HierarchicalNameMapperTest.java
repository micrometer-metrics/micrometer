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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jon Schneider
 */
class HierarchicalNameMapperTest {

    private HierarchicalNameMapper mapper = HierarchicalNameMapper.DEFAULT;

    private SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void buildHierarchicalNameFromDimensionalId() {
        String name = mapper.toHierarchicalName(
                id("http.requests", "method", "GET", "other", "With Spaces", "status", "200"),
                NamingConvention.camelCase);
        assertThat(name).isEqualTo("httpRequests.method.GET.other.With_Spaces.status.200");
    }

    @Test
    void noTags() {
        assertThat(mapper.toHierarchicalName(id("http.requests"), NamingConvention.camelCase))
            .isEqualTo("httpRequests");
    }

    private Meter.Id id(String name, String... tags) {
        return registry.counter(name, tags).getId();
    }

}
