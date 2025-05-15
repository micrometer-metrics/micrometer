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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("deprecation")
class TimeUtilsTest {

    @Test
    void simpleParse() {
        assertThat(TimeUtils.simpleParse("5ns")).isEqualByComparingTo(Duration.ofNanos(5));
        assertThat(TimeUtils.simpleParse("700ms")).isEqualByComparingTo(Duration.ofMillis(700));
        assertThat(TimeUtils.simpleParse("1s")).isEqualByComparingTo(Duration.ofSeconds(1));
        assertThat(TimeUtils.simpleParse("10m")).isEqualByComparingTo(Duration.ofMinutes(10));
        assertThat(TimeUtils.simpleParse("13h")).isEqualByComparingTo(Duration.ofHours(13));
        assertThat(TimeUtils.simpleParse("5d")).isEqualByComparingTo(Duration.ofDays(5));
    }

    @Test
    void simpleParseHandlesSpacesCommasAndUnderscores() {
        assertThat(TimeUtils.simpleParse("7,000 ms")).isEqualByComparingTo(Duration.ofMillis(7000));
        assertThat(TimeUtils.simpleParse("7_000ms ")).isEqualByComparingTo(Duration.ofMillis(7000));
    }

    @Test
    void cantParseDecimal() {
        assertThatThrownBy(() -> TimeUtils.simpleParse("1.1s")).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void formatDuration() {
        assertThat(TimeUtils.format(Duration.ofSeconds(10))).isEqualTo("10s");
        assertThat(TimeUtils.format(Duration.ofSeconds(90))).isEqualTo("1m 30s");
        assertThat(TimeUtils.format(Duration.ofMinutes(2))).isEqualTo("2m");
        assertThat(TimeUtils.format(Duration.ofNanos(1001234000000L))).isEqualTo("16m 41.234s");
    }

}
