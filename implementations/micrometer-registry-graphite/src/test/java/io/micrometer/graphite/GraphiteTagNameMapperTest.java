/**
 * Copyright 2020 Pivotal Software, Inc.
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GraphiteTagNameMapper}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Andrew Fitzgerald
 */
class GraphiteTagNameMapperTest {
    private final GraphiteTagNameMapper nameMapper = new GraphiteTagNameMapper();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final NamingConvention namingConvention = new GraphiteNamingConvention();

    @Test
    void simpleName() {
        Meter meter = registry.counter("a.simple.counter");
        assertThat(getName(meter)).isEqualTo("a.simple.counter");
    }

    @Test
    void nameSubstitutions() {
        Meter meter = registry.counter("a.name with spaces.counter");
        assertThat(getName(meter)).isEqualTo("a.name_with_spaces.counter");
    }

    @Test
    void invalidNameCharacters() {
        Meter meter = registry.counter("a.very=bad;name{.counter");
        assertThat(getName(meter)).isEqualTo("a.very_bad_name_.counter");
    }

    @Test
    void simpleTag() {
        Meter meter = registry.counter("a.simple.counter", "key", "value");
        assertThat(getName(meter)).isEqualTo("a.simple.counter;key=value");
    }

    @Test
    void multipleTags() {
        Meter meter = registry.counter("a.simple.counter", "key", "value", "anotherKey", "another.value");
        assertThat(getName(meter)).isEqualTo("a.simple.counter;key=value;anotherKey=another.value");
    }

    @Test
    void emptyTagKey() {
        Meter meter = registry.counter("a.simple.counter", "", "value");
        assertThat(getName(meter)).isEqualTo("a.simple.counter;unspecified=value");
    }

    @Test
    void emptyTagValue() {
        Meter meter = registry.counter("a.simple.counter", "key", "");
        assertThat(getName(meter)).isEqualTo("a.simple.counter;key=unspecified");
    }

    /**
     *
     * > Tag names must have a length >= 1 and may contain any ascii characters except ;!^=
     * > Tag values must also have a length >= 1, and they may contain any ascii characters except ;~
     * > UTF-8 characters may work for names and values, but they are not well tested and it is not recommended to use non-ascii characters in metric names or tags.
     * - https://graphite.readthedocs.io/en/latest/tags.html#carbon
     *
     */
    @Test
    void terribleTagNaming() {
        Meter meter = registry.counter("a.simple.counter", "key;!^=~ why?", "value;!^=~ why?");
        assertThat(getName(meter)).isEqualTo("a.simple.counter;key____~ why?=value_!^=_ why?");
    }

    private String getName(Meter meter) {
        return nameMapper.toHierarchicalName(meter.getId(), namingConvention);
    }
}
