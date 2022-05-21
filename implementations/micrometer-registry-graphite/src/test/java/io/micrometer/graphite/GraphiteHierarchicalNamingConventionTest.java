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

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GraphiteHierarchicalNamingConvention}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
class GraphiteHierarchicalNamingConventionTest {

    private GraphiteHierarchicalNamingConvention convention = new GraphiteHierarchicalNamingConvention();

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

        GraphiteHierarchicalNamingConvention convention = new GraphiteHierarchicalNamingConvention(
                delegateNamingConvention);

        assertThat(convention.name("my.name", Meter.Type.TIMER)).isEqualTo("name-my.name");
        assertThat(convention.tagKey("my_tag_key")).isEqualTo("key-my_tag_key");
        assertThat(convention.tagValue("my_tag_value")).isEqualTo("value-my_tag_value");
    }

    @Test
    void nameShouldPreserveDot() {
        GraphiteHierarchicalNamingConvention convention = new GraphiteHierarchicalNamingConvention(
                NamingConvention.identity);
        assertThat(convention.name("my.counter", Meter.Type.COUNTER)).isEqualTo("my.counter");
    }

    @Test
    void nameShouldSanitizeSpace() {
        assertThat(convention.name("counter 1", Meter.Type.COUNTER)).isEqualTo("counter_1");
    }

    @Test
    void nameShouldSanitizeQuestionMark() {
        assertThat(convention.name("counter?1", Meter.Type.COUNTER)).isEqualTo("counter_1");
    }

    @Test
    void nameShouldSanitizeColon() {
        assertThat(convention.name("counter:1", Meter.Type.COUNTER)).isEqualTo("counter_1");
    }

    @Test
    void tagKeyShouldSanitizeDot() {
        GraphiteHierarchicalNamingConvention convention = new GraphiteHierarchicalNamingConvention(
                NamingConvention.identity);
        assertThat(convention.tagKey("my.tag")).isEqualTo("my_tag");
    }

    @Test
    void tagKeyShouldSanitizeSpace() {
        assertThat(convention.tagKey("tag 1")).isEqualTo("tag_1");
    }

    @Test
    void tagKeyShouldSanitizeQuestionMark() {
        assertThat(convention.tagKey("tag?1")).isEqualTo("tag_1");
    }

    @Test
    void tagKeyShouldSanitizeColon() {
        assertThat(convention.tagKey("tag:1")).isEqualTo("tag_1");
    }

    @Test
    void tagValueShouldSanitizeDot() {
        assertThat(convention.tagValue("my.value")).isEqualTo("my_value");
    }

    @Test
    void tagValueShouldSanitizeSpace() {
        assertThat(convention.tagKey("value 1")).isEqualTo("value_1");
    }

    @Test
    void tagValueShouldSanitizeQuestionMark() {
        assertThat(convention.tagKey("value?1")).isEqualTo("value_1");
    }

    @Test
    void tagValueShouldSanitizeColon() {
        assertThat(convention.tagKey("value:1")).isEqualTo("value_1");
    }

    private static class CustomNamingConvention implements NamingConvention {

        @Override
        public String name(String name, Meter.Type type, String baseUnit) {
            return "name-" + name;
        }

        @Override
        public String tagKey(String key) {
            return "key-" + key;
        }

        @Override
        public String tagValue(String value) {
            return "value-" + value;
        }

    }

}
