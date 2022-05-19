/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.micrometer.conventions.common;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

class ImmutableKeyValuePairsTests {

    @Test
    void get() {
        Assertions.assertThat(new TestPairs(new Object[0]).get("one")).isNull();
        Assertions.assertThat(new TestPairs(new Object[] { "one", 55 }).get("one")).isEqualTo(55);
        Assertions.assertThat(new TestPairs(new Object[] { "one", 55 }).get("two")).isNull();
        Assertions.assertThat(new TestPairs(new Object[] { "one", 55, "two", "b" }).get("one")).isEqualTo(55);
        Assertions.assertThat(new TestPairs(new Object[] { "one", 55, "two", "b" }).get("two")).isEqualTo("b");
        Assertions.assertThat(new TestPairs(new Object[] { "one", 55, "two", "b" }).get("three")).isNull();
    }

    @Test
    void size() {
        Assertions.assertThat(new TestPairs(new Object[0]).size()).isEqualTo(0);
        Assertions.assertThat(new TestPairs(new Object[] { "one", 55 }).size()).isEqualTo(1);
        Assertions.assertThat(new TestPairs(new Object[] { "one", 55, "two", "b" }).size()).isEqualTo(2);
    }

    @Test
    void isEmpty() {
        Assertions.assertThat(new TestPairs(new Object[0]).isEmpty()).isTrue();
        Assertions.assertThat(new TestPairs(new Object[] { "one", 55 }).isEmpty()).isFalse();
        Assertions.assertThat(new TestPairs(new Object[] { "one", 55, "two", "b" }).isEmpty()).isFalse();
    }

    @Test
    void toStringIsHumanReadable() {
        Assertions.assertThat(new TestPairs(new Object[0]).toString()).isEqualTo("{}");
        Assertions.assertThat(new TestPairs(new Object[] { "one", 55 }).toString()).isEqualTo("{one=55}");
        Assertions.assertThat(new TestPairs(new Object[] { "one", 55, "two", "b" }).toString())
                .isEqualTo("{one=55, two=\"b\"}");
    }

    @Test
    void doesNotCrash() {
        TestPairs pairs = new TestPairs(new Object[0]);
        Assertions.assertThat(pairs.get(null)).isNull();
        Assertions.assertThatCode(() -> pairs.forEach(null)).doesNotThrowAnyException();
    }

    static class TestPairs extends ImmutableKeyValuePairs<String, Object> {

        TestPairs(Object[] data) {
            super(data, Comparator.naturalOrder());
        }

    }

}
