/*
 * Copyright 2020 VMware, Inc.
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
 * Tests for {@link GraphiteDimensionalNamingConvention}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Andrew Fitzgerald
 */
class GraphiteDimensionalNamingConventionTest {

    private GraphiteDimensionalNamingConvention convention = new GraphiteDimensionalNamingConvention();

    @Test
    void name() {
        assertThat(convention.name("name([{id}])/1", Meter.Type.TIMER)).isEqualTo("name___id____1");
    }

    @Test
    void respectDelegateNamingConvention() {
        CustomNamingConvention delegateNamingConvention = new CustomNamingConvention();

        GraphiteDimensionalNamingConvention convention = new GraphiteDimensionalNamingConvention(
                delegateNamingConvention);

        assertThat(convention.name("my.name", Meter.Type.TIMER)).isEqualTo("name-my.name");
        assertThat(convention.tagKey("my_tag_key")).isEqualTo("key-my_tag_key");
        assertThat(convention.tagValue("my_tag_value")).isEqualTo("value-my_tag_value");
    }

    @Test
    void nameShouldPreserveDot() {
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
    void nameShouldSanitizeSemiColon() {
        assertThat(convention.name("counter;1", Meter.Type.COUNTER)).isEqualTo("counter_1");
    }

    @Test
    void nameShouldSanitizeColon() {
        assertThat(convention.name("counter:1", Meter.Type.COUNTER)).isEqualTo("counter_1");
    }

    @Test
    void tagKeyShouldPreserveDot() {
        assertThat(convention.tagKey("my.tag")).isEqualTo("my.tag");
    }

    @Test
    void tagKeyShouldPreserveSpace() {
        assertThat(convention.tagKey("tag 1")).isEqualTo("tag 1");
    }

    @Test
    void tagKeyShouldPreserveQuestionMark() {
        assertThat(convention.tagKey("tag?1")).isEqualTo("tag?1");
    }

    @Test
    void tagKeyShouldPreserveColon() {
        assertThat(convention.tagKey("tag:1")).isEqualTo("tag:1");
    }

    @Test
    void tagKeyShouldSanitizeSemiColon() {
        assertThat(convention.tagKey("tag;1")).isEqualTo("tag_1");
    }

    @Test
    void tagKeyShouldSanitizeExclamation() {
        assertThat(convention.tagKey("tag!1")).isEqualTo("tag_1");
    }

    @Test
    void tagKeyShouldSanitizeCarat() {
        assertThat(convention.tagKey("tag^1")).isEqualTo("tag_1");
    }

    @Test
    void tagKeyShouldSanitizeEquals() {
        assertThat(convention.tagKey("tag=1")).isEqualTo("tag_1");
    }

    @Test
    void tagKeyShouldPreserveTilde() {
        assertThat(convention.tagKey("tag~1")).isEqualTo("tag~1");
    }

    @Test
    void tagKeyShouldHaveLengthGreaterThanZero() {
        assertThat(convention.tagKey("")).isEqualTo("unspecified");
    }

    @Test
    void tagValueShouldPreserveDot() {
        assertThat(convention.tagValue("my.tag")).isEqualTo("my.tag");
    }

    @Test
    void tagValueShouldPreserveSpace() {
        assertThat(convention.tagValue("tag 1")).isEqualTo("tag 1");
    }

    @Test
    void tagValueShouldPreserveQuestionMark() {
        assertThat(convention.tagValue("tag?1")).isEqualTo("tag?1");
    }

    @Test
    void tagValueShouldPreserveColon() {
        assertThat(convention.tagValue("tag:1")).isEqualTo("tag:1");
    }

    @Test
    void tagValueShouldSanitizeSemiColon() {
        assertThat(convention.tagValue("tag;1")).isEqualTo("tag_1");
    }

    @Test
    void tagValueShouldPreserveExclamation() {
        assertThat(convention.tagValue("tag!1")).isEqualTo("tag!1");
    }

    @Test
    void tagValueShouldPreserveCarat() {
        assertThat(convention.tagValue("tag^1")).isEqualTo("tag^1");
    }

    @Test
    void tagValueShouldPreserveEquals() {
        assertThat(convention.tagValue("tag=1")).isEqualTo("tag=1");
    }

    @Test
    void tagValueShouldSanitizeTilde() {
        assertThat(convention.tagValue("tag~1")).isEqualTo("tag_1");
    }

    @Test
    void tagValueShouldHaveLengthGreaterThanZero() {
        assertThat(convention.tagValue("")).isEqualTo("unspecified");
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
