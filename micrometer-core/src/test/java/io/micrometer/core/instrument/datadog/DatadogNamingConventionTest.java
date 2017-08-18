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
package io.micrometer.core.instrument.datadog;

import io.micrometer.core.instrument.Meter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatadogNamingConventionTest {
    private DatadogNamingConvention convention = new DatadogNamingConvention();

    @Test
    void nameStartsWithLetter() {
        assertThat(convention.name("123", Meter.Type.Gauge)).isEqualTo("m_123");
    }

    @Test
    void tagKeyStartsWithLetter() {
        assertThat(convention.tagKey("123")).isEqualTo("m_123");
    }

    @Test
    void dotNotationIsConvertedToCamelCase() {
        assertThat(convention.name("gauge.size", Meter.Type.Gauge)).isEqualTo("gaugeSize");
    }
}
