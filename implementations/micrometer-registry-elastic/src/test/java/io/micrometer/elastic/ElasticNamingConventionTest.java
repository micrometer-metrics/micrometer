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
package io.micrometer.elastic;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.config.NamingConvention;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticNamingConventionTest {

    private final NamingConvention namingConvention = new ElasticNamingConvention();

    @Issue("#506")
    @Test
    void replaceNameTag() {
        assertThat(namingConvention.tagKey("name")).isEqualTo("name_tag");
    }

    @Test
    void replaceTypeTag() {
        assertThat(namingConvention.tagKey("type")).isEqualTo("type_tag");
    }

    @Test
    void replaceLeadingUnderscores() {
        assertThat(namingConvention.tagKey("__tag")).isEqualTo("tag");
    }

}
