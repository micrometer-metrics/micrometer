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
package io.micrometer.graphite;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.NamingConvention;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GraphiteHierarchicalNameMapperTest {
    private final GraphiteHierarchicalNameMapper nameMapper = new GraphiteHierarchicalNameMapper("stack", "app.name");
    private final Meter.Id id = new Meter.Id("my.name", Tags.of("app.name", "MYAPP", "stack", "PROD", "other.tag", "value"),
            null, null, Meter.Type.COUNTER);

    @Issue("#561")
    @Test
    void tagsAsPrefix() {
        assertThat(nameMapper.toHierarchicalName(id, NamingConvention.camelCase))
                .isEqualTo("PROD.MYAPP.myName.otherTag.value");
    }
}