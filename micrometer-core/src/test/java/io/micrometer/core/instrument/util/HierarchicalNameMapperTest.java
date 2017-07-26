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

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jon Schneider
 */
class HierarchicalNameMapperTest {

    @Test
    void buildHierarchicalNameFromDimensionalId() {
        HierarchicalNameMapper mapper = new HierarchicalNameMapper()
                .setValueSeparator("-");

        String name = mapper.toHierarchicalName("http_requests", Tags.zip("status", "200", "method", "GET"));
        assertThat(name).isEqualTo("http_requests.method-GET.status-200");
    }
}
