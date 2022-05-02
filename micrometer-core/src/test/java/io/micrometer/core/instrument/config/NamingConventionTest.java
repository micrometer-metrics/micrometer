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
package io.micrometer.core.instrument.config;

import io.micrometer.core.instrument.Meter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Tests for {@link NamingConvention}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
class NamingConventionTest {

    @Test
    void camelCaseName() {
        String name = NamingConvention.camelCase.name("a.Name.with.Words", Meter.Type.COUNTER);
        assertThat(name).isEqualTo("aNameWithWords");
    }

    @Test
    void camelCaseTagKey() {
        String name = NamingConvention.camelCase.tagKey("a.Name.with.Words");
        assertThat(name).isEqualTo("aNameWithWords");
    }

    @Test
    void snakeCaseName() {
        String name = NamingConvention.snakeCase.name("a.Name.with.Words", Meter.Type.COUNTER);
        assertThat(name).isEqualTo("a_Name_with_Words");
    }

    @Test
    void snakeCaseTagKey() {
        String name = NamingConvention.snakeCase.tagKey("a.Name.with.Words");
        assertThat(name).isEqualTo("a_Name_with_Words");
    }

    @Test
    void upperCamelCaseName() {
        String name = NamingConvention.upperCamelCase.name("a.name.with.words", Meter.Type.COUNTER);
        assertThat(name).isEqualTo("ANameWithWords");
    }

    @Test
    void upperCamelCaseTagKey() {
        String name = NamingConvention.upperCamelCase.tagKey("a.name.with.words");
        assertThat(name).isEqualTo("ANameWithWords");
    }

}
