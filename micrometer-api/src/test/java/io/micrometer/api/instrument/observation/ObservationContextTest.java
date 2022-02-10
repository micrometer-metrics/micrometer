/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.api.instrument.observation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Observation.Context}.
 *
 * @author Jonatan Ivanov
 */
class ObservationContextTest {
    private Observation.Context context;

    @BeforeEach
    void setUp() {
        this.context = new Observation.Context();
    }

    @Test
    void shouldBeEmptyByDefault() {
        assertThat(context.get(String.class)).isNull();
    }

    @Test
    void getShouldReturnWhatWasPutPreviously() {
        assertThat(context.put(String.class, "42")).isSameAs(context);
        assertThat(context.get(String.class)).isEqualTo("42");

        assertThat(context.put(Integer.class, 123)).isSameAs(context);
        assertThat(context.get(Integer.class)).isEqualTo(123);
    }

    @Test
    void overwrittenValuesShouldBeUpdated() {
        context
                .put(String.class, "42")
                .put(Integer.class, 123)
                .put(String.class, "24");
        assertThat(context.get(String.class)).isEqualTo("24");
        assertThat(context.get(Integer.class)).isEqualTo(123);
    }

    @Test
    void removedItemsShouldNotBePresent() {
        context
                .put(String.class, "42")
                .put(Integer.class, 123)
                .remove(String.class);
        assertThat(context.get(Integer.class)).isEqualTo(123);
        assertThat(context.get(String.class)).isNull();
    }

    @Test
    void removeNonExistingItemShouldNotFail() {
        context.remove(String.class);
    }

    @Test
    void getOrDefaultShouldUseFallbackValue() {
        context.put(String.class, "42");
        assertThat(context.getOrDefault(String.class, "abc")).isEqualTo("42");
        assertThat(context.getOrDefault(Integer.class, 123)).isEqualTo(123);
    }

    @Test
    void computeIfAbsentShouldUseFallbackValue() {
        context.put(String.class, "42");
        assertThat(context.computeIfAbsent(String.class, clazz -> "abc")).isEqualTo("42");
        assertThat(context.get(String.class)).isEqualTo("42");

        assertThat(context.computeIfAbsent(Integer.class, clazz -> 123)).isEqualTo(123);
        assertThat(context.get(Integer.class)).isEqualTo(123);
    }
}
