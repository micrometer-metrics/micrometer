/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.influx;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class InfluxNamingConventionTest {
    private InfluxNamingConvention convention = new InfluxNamingConvention(NamingConvention.snakeCase);

    @Test
    void name() {
        assertThat(convention.name("foo=, bar", Meter.Type.GAUGE)).isEqualTo("foo_\\,\\ bar");
    }

    @Test
    void tagKey() {
        assertThat(convention.tagKey("foo=, bar")).isEqualTo("foo\\=\\,\\ bar");
    }

    @Test
    void tagValue() {
        assertThat(convention.tagValue("foo=, bar")).isEqualTo("foo\\=\\,\\ bar");
    }

    @Test
    void timeCannotBeATagKeyOrValue() {
        assertThat(catchThrowable(() -> convention.tagKey("time"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(catchThrowable(() -> convention.tagValue("time"))).isInstanceOf(IllegalArgumentException.class);
    }
}
