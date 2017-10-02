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
package io.micrometer.core.instrument.util;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.NamingConvention;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * @author Jon Schneider
 */
class HierarchicalNameMapperTest {
    private HierarchicalNameMapper mapper = HierarchicalNameMapper.DEFAULT;

    @Test
    void buildHierarchicalNameFromDimensionalId() {
        String name = mapper.toHierarchicalName(
            createId("httpRequests", Tags.zip("method", "GET", "other", "With Spaces", "status", "200")),
            NamingConvention.camelCase
        );
        assertThat(name).isEqualTo("httpRequests.method.GET.other.With_Spaces.status.200");
    }

    private Meter.Id createId(String conventionName, List<Tag> conventionTags) {
        return new Meter.Id() {
            @Override
            public String getName() {
                fail("should not be used");
                return null;
            }

            @Override
            public Iterable<Tag> getTags() {
                return conventionTags;
            }

            @Override
            public String getBaseUnit() {
                return null;
            }

            @Override
            public String getDescription() {
                return null;
            }

            @Override
            public String getConventionName(NamingConvention convention) {
                return conventionName;
            }

            @Override
            public List<Tag> getConventionTags(NamingConvention convention) {
                return conventionTags;
            }

            @Override
            public void setType(Meter.Type type) {
            }

            @Override
            public void setBaseUnit(String baseUnit) {
            }
        };
    }

    @Test
    void noTags() {
        assertThat(mapper.toHierarchicalName(createId("httpRequests", emptyList()), NamingConvention.camelCase))
            .isEqualTo("httpRequests");
    }
}
