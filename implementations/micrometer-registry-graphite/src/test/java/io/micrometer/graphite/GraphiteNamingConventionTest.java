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
package io.micrometer.graphite;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GraphiteNamingConvention}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
class GraphiteNamingConventionTest {
    private GraphiteNamingConvention convention = new GraphiteNamingConvention();

    @Test
    void name() {
        assertThat(convention.name("name([{id}])/1", Meter.Type.TIMER)).isEqualTo("name___id____1");
    }

    @Test
    void dotNotationIsConvertedToCamelCase() {
        assertThat(convention.name("gauge.size", Meter.Type.GAUGE)).isEqualTo("gaugeSize");
    }

    @Test
    void respectDelegateNamingConvention() {
        CustomNamingConvention delegateNamingConvention = new CustomNamingConvention();

        GraphiteNamingConvention convention = new GraphiteNamingConvention(delegateNamingConvention);

        assertThat(convention.name("my.name", Meter.Type.TIMER)).isEqualTo("name: my.name");
        assertThat(convention.tagKey("my.tag.key")).isEqualTo("key: my.tag.key");
        assertThat(convention.tagValue("my.tag.value")).isEqualTo("value: my.tag.value");
    }

    private static class CustomNamingConvention implements NamingConvention {

        @Override
        public String name(String name, Meter.Type type, String baseUnit) {
            return "name: " + name;
        }

        @Override
        public String tagKey(String key) {
            return "key: " + key;
        }

        @Override
        public String tagValue(String value) {
            return "value: " + value;
        }

    }

}
