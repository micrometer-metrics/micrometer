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
package io.micrometer.core.instrument;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Meter.Id}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
class MeterIdTest {

    @Test
    void withStatistic() {
        Meter.Id id = new Meter.Id("my.id", Tags.empty(), null, null, Meter.Type.TIMER);
        assertThat(id.withTag(Statistic.TOTAL_TIME).getTags()).contains(Tag.of("statistic", "total"));
    }

    @Test
    void equalsAndHashCode() {
        Meter.Id id = new Meter.Id("my.id", Tags.empty(), null, null, Meter.Type.COUNTER);
        Meter.Id id2 = new Meter.Id("my.id", Tags.empty(), null, null, Meter.Type.COUNTER);

        assertThat(id).isEqualTo(id2);
        assertThat(id.hashCode()).isEqualTo(id2.hashCode());
    }

    @Test
    void withTags() {
        Meter.Id id = new Meter.Id("my.id", Tags.of("k1", "v1", "k2", "v2"), null, null, Meter.Type.COUNTER);
        Meter.Id newId = id.withTags(Tags.of("k1", "n1", "k", "n"));
        assertThat(newId.getTags()).containsExactlyElementsOf(Tags.of("k2", "v2", "k1", "n1", "k", "n"));
    }

    @Test
    void replaceTags() {
        Meter.Id id = new Meter.Id("my.id", Tags.of("k1", "v1", "k2", "v2"), null, null, Meter.Type.COUNTER);
        Meter.Id newId = id.replaceTags(Tags.of("k1", "n1", "k", "n"));
        assertThat(newId.getTags()).containsExactlyElementsOf(Tags.of("k1", "n1", "k", "n"));
    }

}
