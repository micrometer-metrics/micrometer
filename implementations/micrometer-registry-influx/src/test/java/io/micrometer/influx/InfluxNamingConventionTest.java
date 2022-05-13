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
package io.micrometer.influx;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Tests for {@link InfluxNamingConvention}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
class InfluxNamingConventionTest {

    private InfluxNamingConvention convention = new InfluxNamingConvention(NamingConvention.snakeCase);

    @Issue("#693")
    @Test
    void name() {
        assertThat(convention.name("foo.bar=, baz", Meter.Type.GAUGE)).isEqualTo("foo_bar_\\,\\ baz");
    }

    @Test
    void tagKey() {
        assertThat(convention.tagKey("foo.bar=, baz")).isEqualTo("foo_bar\\=\\,\\ baz");
    }

    @Test
    void tagValue() {
        assertThat(convention.tagValue("foo=, bar")).isEqualTo("foo\\=\\,\\ bar");
    }

    @Test
    void timeCannotBeATagKey() {
        assertThat(catchThrowable(() -> convention.tagKey("time"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void timeCanBeATagValue() {
        assertThat(convention.tagValue("time")).isEqualTo("time");
    }

    @Issue("#645")
    @Test
    void namingConventionIsNotAppliedToTagValues() {
        // ...but escaping of special characters still applies
        assertThat(convention.tagValue("org.example.service=")).isEqualTo("org.example.service\\=");
    }

    @Test
    void respectDelegateNamingConvention() {
        CustomNamingConvention delegateNamingConvention = new CustomNamingConvention();

        InfluxNamingConvention convention = new InfluxNamingConvention(delegateNamingConvention);

        assertThat(convention.name("my.name", Meter.Type.TIMER)).isEqualTo("name:my.name");
        assertThat(convention.tagKey("my.tag.key")).isEqualTo("key:my.tag.key");
        assertThat(convention.tagValue("my.tag.value")).isEqualTo("value:my.tag.value");
    }

    @Issue("#2155")
    @Test
    void newlineCharReplacedInTagValues() {
        assertThat(convention.tagValue("hello\nworld\n")).isEqualTo("hello\\ world\\ ");
    }

    private static class CustomNamingConvention implements NamingConvention {

        @Override
        public String name(String name, Meter.Type type, String baseUnit) {
            return "name:" + name;
        }

        @Override
        public String tagKey(String key) {
            return "key:" + key;
        }

        @Override
        public String tagValue(String value) {
            return "value:" + value;
        }

    }

}
