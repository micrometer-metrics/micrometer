/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.config.NamingConvention;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class NamingConventionTest {
    @Test
    void camelCase() {
        String name = NamingConvention.camelCase.name("a.Name.with.Words", Meter.Type.COUNTER);
        assertThat(name).isEqualTo("aNameWithWords");
    }

    @Test
    void snakeCase() {
        String name = NamingConvention.snakeCase.name("a.Name.with.Words", Meter.Type.COUNTER);
        assertThat(name).isEqualTo("a_Name_with_Words");
    }

    @Test
    void upperCamelCase() {
        String name = NamingConvention.upperCamelCase.name("a.name.with.words", Meter.Type.COUNTER);
        assertThat(name).isEqualTo("ANameWithWords");
    }
}