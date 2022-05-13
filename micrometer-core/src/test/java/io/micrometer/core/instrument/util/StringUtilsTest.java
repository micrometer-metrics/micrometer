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
package io.micrometer.core.instrument.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link StringUtils}.
 *
 * @author Johnny Lim
 */
class StringUtilsTest {

    @Test
    void truncateWhenGreaterThanMaxLengthShouldTruncate() {
        assertThat(StringUtils.truncate("1234567890", 5)).isEqualTo("12345");
    }

    @Test
    void truncateWhenLessThanMaxLengthShouldReturnItself() {
        assertThat(StringUtils.truncate("123", 5)).isEqualTo("123");
    }

    @Test
    void truncateWithIndicatorWhenGreaterThanMaxLengthShouldTruncate() {
        assertThat(StringUtils.truncate("1234567890", 7, "...")).isEqualTo("1234...");
    }

    @Test
    void truncateWithEmptyIndicatorWhenGreaterThanMaxLengthShouldTruncate() {
        assertThat(StringUtils.truncate("1234567890", 7, "")).isEqualTo("1234567");
    }

    @Test
    void truncateWithIndicatorWhenSameAsMaxLengthShouldReturnItself() {
        assertThat(StringUtils.truncate("1234567", 7, "...")).isEqualTo("1234567");
    }

    @Test
    void truncateWithIndicatorWhenLessThanMaxLengthShouldReturnItself() {
        assertThat(StringUtils.truncate("123", 7, "...")).isEqualTo("123");
    }

    @Test
    void truncateWithIndicatorThrowsOnInvalidLengthWhenOriginalStringIsShort() {
        assertThrows(IllegalArgumentException.class, () -> StringUtils.truncate("12345", 7, "[abbreviated]"));
    }

    @Test
    void truncateWithIndicatorThrowsOnInvalidLengthWhenOriginalStringIsLongEnough() {
        assertThrows(IllegalArgumentException.class, () -> StringUtils.truncate("1234567890", 7, "[abbreviated]"));
    }

    @Test
    void isNotEmptyWhenNullShouldBeFalse() {
        assertThat(StringUtils.isNotEmpty(null)).isFalse();
    }

    @Test
    void isNotEmptyWhenEmptyShouldBeFalse() {
        assertThat(StringUtils.isNotEmpty("")).isFalse();
    }

    @Test
    void isNotEmptyWhenHasAnyCharacterShouldBeFalse() {
        assertThat(StringUtils.isNotEmpty(" ")).isTrue();
    }

}
