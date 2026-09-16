/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.core.instrument.util;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MeterEquivalence}.
 */
class MeterEquivalenceTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void equalsWhenBothNullShouldReturnTrue() {
        assertThat(MeterEquivalence.equals(null, null)).isTrue();
    }

    @Test
    void equalsWhenOnlyFirstNullShouldReturnFalse() {
        Meter meter = registry.counter("my.counter");
        assertThat(MeterEquivalence.equals(null, meter)).isFalse();
    }

    @Test
    void equalsWhenOnlySecondNullShouldReturnFalse() {
        Meter meter = registry.counter("my.counter");
        assertThat(MeterEquivalence.equals(meter, null)).isFalse();
    }

    @Test
    void equalsWhenSameInstanceShouldReturnTrue() {
        Meter meter = registry.counter("my.counter");
        assertThat(MeterEquivalence.equals(meter, meter)).isTrue();
    }

    @Test
    void equalsWhenOtherIsNotAMeterShouldReturnFalse() {
        Meter meter = registry.counter("my.counter");
        assertThat(MeterEquivalence.equals(meter, "not a meter")).isFalse();
    }

    @Test
    void equalsWhenSameIdShouldReturnTrue() {
        Meter meter1 = registry.counter("my.counter");
        Meter meter2 = registry.counter("my.counter");
        assertThat(MeterEquivalence.equals(meter1, meter2)).isTrue();
    }

    @Test
    void equalsWhenDifferentIdShouldReturnFalse() {
        Meter meter1 = registry.counter("my.counter");
        Meter meter2 = registry.counter("my.other.counter");
        assertThat(MeterEquivalence.equals(meter1, meter2)).isFalse();
    }

}
