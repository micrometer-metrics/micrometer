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

class RequiredSearchTest {

    private MeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void addMeters() {
        registry.counter("my.counter", "k", "v");
        registry.counter("my.counter", "k", "v", "k2", "v2");
        registry.counter("my.other.counter", "k", "v", "k2", "v3");
        registry.timer("my.timer", "k", "v");
        registry.gauge("gauge", 0);
    }

    @Test
    void allMeters() {
        assertThat(RequiredSearch.in(registry).meters()).hasSize(5);
    }

    @Test
    void allMetersWithName() {
        assertThat(RequiredSearch.in(registry).name("my.counter").meters()).hasSize(2);
        // just pick one of the matching ones
        assertThat(RequiredSearch.in(registry).name("my.counter").counter()).isNotNull();

        assertThat(RequiredSearch.in(registry).name(n -> n.startsWith("my")).meters()).hasSize(4);
        assertThat(RequiredSearch.in(registry).name(n -> n.startsWith("my")).timer()).isNotNull();
    }

    @Test
    void allMetersWithTag() {
        assertThat(RequiredSearch.in(registry).tag("k2", "v2").meters()).hasSize(1);

        assertThatThrownBy(() -> RequiredSearch.in(registry).tag("k2", "WRONG").meters())
            .isInstanceOf(MeterNotFoundException.class);

        assertThatThrownBy(() -> RequiredSearch.in(registry).tag("WRONG", "v2").meters())
            .isInstanceOf(MeterNotFoundException.class);

        assertThat(RequiredSearch.in(registry).tags("k2", "v2").meters()).hasSize(1);
        assertThat(RequiredSearch.in(registry).tags("k", "v", "k2", "v2").meters()).hasSize(1);

        assertThatThrownBy(() -> RequiredSearch.in(registry).tags("k", "k2", "k3"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void multipleNamesMatchMultipleWrongTypes() {
        assertThatThrownBy(() -> RequiredSearch.in(registry).name(n -> n.startsWith("my")).gauge())
            .isInstanceOf(MeterNotFoundException.class)
            .hasMessageContaining("Meters with names ['my.counter', 'my.other.counter', 'my.timer'] were found")
            .hasMessageContaining("Expected to find a gauge. Types found were [counter, timer]");
    }

    @Test
    void nameMatchesSingleWrongType() {
        assertThatThrownBy(() -> RequiredSearch.in(registry).name(n -> n.contains("counter")).gauge())
            .isInstanceOf(MeterNotFoundException.class)
            .hasMessageContaining("Expected to find a gauge. The only type found was a counter");
    }

    @Test
    void noMatchingExactName() {
        assertThatThrownBy(() -> RequiredSearch.in(registry).name("does.not.exist").counter())
            .isInstanceOf(MeterNotFoundException.class)
            .hasMessageContaining("No meter with name 'does.not.exist' was found")
            .hasMessageContaining("Meters with type counter were found");
    }

    @Test
    void noMatchingNamePredicate() {
        assertThatThrownBy(() -> RequiredSearch.in(registry).name(n -> n.equals("does.not.exist")).counter())
            .isInstanceOf(MeterNotFoundException.class)
            .hasMessageContaining("No meter that matches the name predicate was found")
            .hasMessageContaining("Meters with type counter were found");
    }

    @Test
    void matchingAndNoMatchingRequiredTagKey() {
        assertThatThrownBy(() -> RequiredSearch.in(registry).tagKeys("k", "k2").timer())
            .isInstanceOf(MeterNotFoundException.class)
            .hasMessageContaining("A meter with name 'my.timer' has the required tag 'k'")
            .hasMessageContaining("No meters have the required tag 'k2'");
    }

    @Test
    void multipleMatchingRequiredTagKey() {
        assertThatThrownBy(() -> RequiredSearch.in(registry).tagKeys("k", "does.not.exist").counter())
            .isInstanceOf(MeterNotFoundException.class)
            .hasMessageContaining("Meters with names ['my.counter', 'my.other.counter'] have the required tag 'k'");
    }

    @Test
    void multipleNonMatchingRequiredTag() {
        assertThatThrownBy(() -> RequiredSearch.in(registry).name("my.counter").tag("k", "does.not.exist").counter())
            .isInstanceOf(MeterNotFoundException.class)
            .hasMessageContaining("No meters have a tag 'k' with value 'does.not.exist'. The only value found was 'v'");
    }

    @Test
    void multipleNonMatchingRequiredTagWithMultipleOtherValues() {
        assertThatThrownBy(() -> RequiredSearch.in(registry).tag("k2", "does.not.exist").counter())
            .isInstanceOf(MeterNotFoundException.class)
            .hasMessageContaining(
                    "No meters have a tag 'k2' with value 'does.not.exist'. Tag values found were ['v2', 'v3']");
    }

    @Test
    void singleMatchingRequiredTag() {
        assertThatThrownBy(() -> RequiredSearch.in(registry).tag("k", "v").tagKeys("does.not.exist").timer())
            .isInstanceOf(MeterNotFoundException.class)
            .hasMessageContaining("A meter with name 'my.timer' has a tag 'k' with value 'v'");
    }

    @Test
    void multipleMatchingRequiredTag() {
        assertThatThrownBy(() -> RequiredSearch.in(registry).tag("k", "v").tagKeys("does.not.exist").counter())
            .isInstanceOf(MeterNotFoundException.class)
            .hasMessageContaining("Meters with names ['my.counter', 'my.other.counter'] have a tag 'k' with value 'v'");
    }

    @Test
    void noMatchingType() {
        assertThatThrownBy(() -> RequiredSearch.in(registry).functionCounter())
            .isInstanceOf(MeterNotFoundException.class)
            .hasMessageContaining("No meters with type function counter were found");
    }

    @Test
    void allMetersWithTagKey() {
        assertThat(RequiredSearch.in(registry).tagKeys("k", "k2").counter()).isNotNull();
    }

}
