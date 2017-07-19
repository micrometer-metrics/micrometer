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
package io.micrometer.core.instrument.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static io.micrometer.core.instrument.Tags.zip;

class MeterIdTest {

    @Test
    void idEqualityAndHashCode() {
        MeterId id1 = new MeterId("foo", zip("k1", "v1", "k2", "v2"));
        MeterId id2 = new MeterId("foo", zip("k1", "v1", "k2", "v2"));
        MeterId id3 = new MeterId("foo", zip("k2", "v2", "k1", "v1"));
        MeterId id4 = new MeterId("foo", zip("k1", "v1"));
        MeterId id5 = new MeterId("bar", zip("k1", "v1", "k2", "v2"));

        assertThat(id1)
                .isEqualTo(id2)
                .isEqualTo(id3)
                .isNotEqualTo(id4)
                .isNotEqualTo(id5);

        assertThat(id1.hashCode())
                .isEqualTo(id2.hashCode())
                .isEqualTo(id3.hashCode())
                .isNotEqualTo(id4.hashCode())
                .isNotEqualTo(id5.hashCode());
    }
}
