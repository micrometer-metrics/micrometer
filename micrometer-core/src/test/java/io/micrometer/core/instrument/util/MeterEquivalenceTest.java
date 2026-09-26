/*
 * Copyright 2024 VMware, Inc.
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

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MeterEquivalence}.
 */
class MeterEquivalenceTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final Meter meter1 = Counter.builder("counter.one").register(registry);

    private final Meter meter2 = Counter.builder("counter.two").register(registry);

    @Test
    @Issue("#7934")
    void equalsNullNullReturnsTrue() {
        assertThat(MeterEquivalence.equals(null, null)).isTrue();
    }

    @Test
    void equalsNullNonNullReturnsFalse() {
        assertThat(MeterEquivalence.equals(null, meter1)).isFalse();
    }

    @Test
    void equalsNonNullNullReturnsFalse() {
        assertThat(MeterEquivalence.equals(meter1, null)).isFalse();
    }

    @Test
    void equalsSameInstanceReturnsTrue() {
        assertThat(MeterEquivalence.equals(meter1, meter1)).isTrue();
    }

    @Test
    void equalsNonMeterReturnsFalse() {
        assertThat(MeterEquivalence.equals(meter1, "not a meter")).isFalse();
    }

    @Test
    void equalsDifferentMetersReturnsFalse() {
        assertThat(MeterEquivalence.equals(meter1, meter2)).isFalse();
    }

    @Test
    void equalsMetersWithSameIdReturnsTrue() {
        Meter duplicate = Counter.builder("counter.one").register(new SimpleMeterRegistry());
        assertThat(MeterEquivalence.equals(meter1, duplicate)).isTrue();
    }

}
