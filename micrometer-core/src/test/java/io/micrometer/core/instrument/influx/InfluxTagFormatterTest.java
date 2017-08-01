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
package io.micrometer.core.instrument.influx;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class InfluxTagFormatterTest {
    private InfluxTagFormatter formatter = new InfluxTagFormatter();

    @Test
    void formatName() {
        assertThat(formatter.formatName("foo=, bar")).isEqualTo("foo_\\,\\ bar");
    }

    @Test
    void formatTagKey() {
        assertThat(formatter.formatTagKey("foo=, bar")).isEqualTo("foo\\=\\,\\ bar");
    }

    @Test
    void formatTagValue() {
        assertThat(formatter.formatTagValue("foo=, bar")).isEqualTo("foo\\=\\,\\ bar");
    }

    @Test
    void timeCannotBeATagKeyOrValue() {
        assertThat(catchThrowable(() -> formatter.formatTagKey("time"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(catchThrowable(() -> formatter.formatTagValue("time"))).isInstanceOf(IllegalArgumentException.class);
    }
}
