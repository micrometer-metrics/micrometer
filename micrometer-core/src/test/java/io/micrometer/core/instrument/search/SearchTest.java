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
package io.micrometer.core.instrument.search;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchTest {

    private MeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void addMeters() {
        registry.counter("my.counter", "k", "v");
        registry.counter("my.counter", "k", "v", "k2", "v2");
        registry.timer("my.timer", "k", "v");
    }

    @Test
    void allMeters() {
        assertThat(Search.in(registry).meters()).hasSize(3);
    }

    @Test
    void allMetersWithName() {
        assertThat(Search.in(registry).name("my.counter").meters()).hasSize(2);
        // just pick one of the matching ones
        assertThat(Search.in(registry).name("my.counter").counter()).isNotNull();

        assertThat(Search.in(registry).name(n -> n.startsWith("my")).meters()).hasSize(3);
        assertThat(Search.in(registry).name(n -> n.startsWith("my")).timer()).isNotNull();
    }

    @Test
    void allMetersWithTag() {
        assertThat(Search.in(registry).tag("k2", "v2").meters()).hasSize(1);
        assertThat(Search.in(registry).tag("k2", "WRONG").meters()).isEmpty();
        assertThat(Search.in(registry).tag("WRONG", "v2").meters()).isEmpty();

        assertThat(Search.in(registry).tags("k2", "v2").meters()).hasSize(1);
        assertThat(Search.in(registry).tags("k", "v", "k2", "v2").meters()).hasSize(1);

        assertThatThrownBy(() -> Search.in(registry).tags("k", "k2", "k3"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allMetersWithTagByPredicate() {
        assertThat(Search.in(registry).tag("k", v -> v.equals("v")).meters()).hasSize(3);
        assertThat(Search.in(registry).tag("k2", v -> v.equals("v")).meters()).isEmpty();
    }

    @Test
    void allMetersWithTagKey() {
        assertThat(Search.in(registry).tagKeys("k", "k2").counter()).isNotNull();
    }

}
