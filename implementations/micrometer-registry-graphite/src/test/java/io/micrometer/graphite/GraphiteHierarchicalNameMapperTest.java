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
package io.micrometer.graphite;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GraphiteHierarchicalNameMapper}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
class GraphiteHierarchicalNameMapperTest {

    private final GraphiteHierarchicalNameMapper nameMapper = new GraphiteHierarchicalNameMapper("stack", "app.name");

    private final Meter.Id id = new SimpleMeterRegistry()
        .counter("my.name", "app.name", "MYAPP", "stack", "PROD", "other.tag", "value")
        .getId();

    @Issue("#561")
    @Test
    void tagsAsPrefix() {
        assertThat(nameMapper.toHierarchicalName(id, NamingConvention.camelCase))
            .isEqualTo("PROD.MYAPP.myName.otherTag.value");
    }

    @Test
    void toHierarchicalNameShouldSanitizeTagValueFromTagsAsPrefix() {
        Meter.Id id = new SimpleMeterRegistry()
            .counter("my.name", "app.name", "MY APP", "stack", "PROD", "other.tag", "value")
            .getId();
        assertThat(nameMapper.toHierarchicalName(id, new GraphiteHierarchicalNamingConvention()))
            .isEqualTo("PROD.MY_APP.myName.otherTag.value");
    }

}
