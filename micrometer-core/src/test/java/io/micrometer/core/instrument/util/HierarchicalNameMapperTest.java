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

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.NamingConvention;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * @author Jon Schneider
 */
class HierarchicalNameMapperTest {

    @Test
    void buildHierarchicalNameFromDimensionalId() {
        HierarchicalNameMapper mapper = HierarchicalNameMapper.DEFAULT;
        String name = mapper.toHierarchicalName(
            new Meter.Id() {
                @Override
                public String getName() {
                    fail("should not be used");
                    return null;
                }

                @Override
                public Iterable<Tag> getTags() {
                    fail("should not be used");
                    return null;
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
                    return "httpRequests";
                }

                @Override
                public List<Tag> getConventionTags(NamingConvention convention) {
                    return Tags.zip("status", "200", "method", "GET",
                        "other", "With Spaces");
                }

                @Override
                public void setType(Meter.Type type) {
                }

                @Override
                public void setBaseUnit(String baseUnit) {
                }
            },
            NamingConvention.snakeCase
        );
        assertThat(name).isEqualTo("httpRequests.method.GET.other.With_Spaces.status.200");
    }
}
