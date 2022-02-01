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

import io.micrometer.api.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Observation.Context}.
 *
 * @author Jonatan Ivanov
 */
class HandlerContextTest {
    private Observation.Context handlerContext;

    @BeforeEach
    void setUp() {
        this.handlerContext = new Observation.Context();
    }

    @Test
    void shouldBeEmptyByDefault() {
        assertThat(handlerContext.get(String.class)).isNull();
    }

    @Test
    void getShouldReturnWhatWasPutPreviously() {
        assertThat(handlerContext.put(String.class, "42")).isSameAs(handlerContext);
        assertThat(handlerContext.get(String.class)).isEqualTo("42");

        assertThat(handlerContext.put(Integer.class, 123)).isSameAs(handlerContext);
        assertThat(handlerContext.get(Integer.class)).isEqualTo(123);
    }

    @Test
    void overwrittenValuesShouldBeUpdated() {
        handlerContext
                .put(String.class, "42")
                .put(Integer.class, 123)
                .put(String.class, "24");
        assertThat(handlerContext.get(String.class)).isEqualTo("24");
        assertThat(handlerContext.get(Integer.class)).isEqualTo(123);
    }

    @Test
    void removedItemsShouldNotBePresent() {
        handlerContext
                .put(String.class, "42")
                .put(Integer.class, 123)
                .remove(String.class);
        assertThat(handlerContext.get(Integer.class)).isEqualTo(123);
        assertThat(handlerContext.get(String.class)).isNull();
    }

    @Test
    void removeNonExistingItemShouldNotFail() {
        handlerContext.remove(String.class);
    }

    @Test
    void getOrDefaultShouldUseFallbackValue() {
        handlerContext.put(String.class, "42");
        assertThat(handlerContext.getOrDefault(String.class, "abc")).isEqualTo("42");
        assertThat(handlerContext.getOrDefault(Integer.class, 123)).isEqualTo(123);
    }

    @Test
    void computeIfAbsentShouldUseFallbackValue() {
        handlerContext.put(String.class, "42");
        assertThat(handlerContext.computeIfAbsent(String.class, clazz -> "abc")).isEqualTo("42");
        assertThat(handlerContext.get(String.class)).isEqualTo("42");

        assertThat(handlerContext.computeIfAbsent(Integer.class, clazz -> 123)).isEqualTo(123);
        assertThat(handlerContext.get(Integer.class)).isEqualTo(123);
    }
}
