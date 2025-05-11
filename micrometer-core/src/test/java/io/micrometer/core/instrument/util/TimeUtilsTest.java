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

import java.util.concurrent.TimeUnit;
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

    @Test
    void convert() {
        // double
        assertThat(TimeUtils.convert(60_000.0, TimeUnit.NANOSECONDS, TimeUnit.MICROSECONDS)).isEqualTo(60.0);
        assertThat(TimeUtils.convert(60_000.0, TimeUnit.MICROSECONDS, TimeUnit.MILLISECONDS)).isEqualTo(60.0);
        assertThat(TimeUtils.convert(60_000.0, TimeUnit.MILLISECONDS, TimeUnit.SECONDS)).isEqualTo(60.0);
        assertThat(TimeUtils.convert(60.0, TimeUnit.SECONDS, TimeUnit.MINUTES)).isEqualTo(1.0);
        assertThat(TimeUtils.convert(60.0, TimeUnit.MINUTES, TimeUnit.HOURS)).isEqualTo(1.0);
        assertThat(TimeUtils.convert(24.0, TimeUnit.HOURS, TimeUnit.DAYS)).isEqualTo(1.0);
        assertThat(TimeUtils.convert(1.0, TimeUnit.DAYS, TimeUnit.DAYS)).isEqualTo(1.0);

        // long
        assertThat(TimeUtils.convert(60_000, TimeUnit.NANOSECONDS, TimeUnit.MICROSECONDS)).isEqualTo(60);
        assertThat(TimeUtils.convert(60_000, TimeUnit.MICROSECONDS, TimeUnit.MILLISECONDS)).isEqualTo(60);
        assertThat(TimeUtils.convert(60_000, TimeUnit.MILLISECONDS, TimeUnit.SECONDS)).isEqualTo(60);
        assertThat(TimeUtils.convert(60, TimeUnit.SECONDS, TimeUnit.MINUTES)).isEqualTo(1);
        assertThat(TimeUtils.convert(60, TimeUnit.MINUTES, TimeUnit.HOURS)).isEqualTo(1);
        assertThat(TimeUtils.convert(24, TimeUnit.HOURS, TimeUnit.DAYS)).isEqualTo(1);
        assertThat(TimeUtils.convert(1, TimeUnit.DAYS, TimeUnit.DAYS)).isEqualTo(1);
    }

    @Test
    void nanosToUnit() {
        // double
        assertThat(TimeUtils.nanosToUnit(60_000.0, TimeUnit.NANOSECONDS)).isEqualTo(60_000.0);
        assertThat(TimeUtils.nanosToUnit(60_000.0, TimeUnit.MICROSECONDS)).isEqualTo(60.0);
        assertThat(TimeUtils.nanosToUnit(60_000_000.0, TimeUnit.MILLISECONDS)).isEqualTo(60.0);
        assertThat(TimeUtils.nanosToUnit(60_000_000_000.0, TimeUnit.SECONDS)).isEqualTo(60.0);
        assertThat(TimeUtils.nanosToUnit(60_000_000_000.0, TimeUnit.MINUTES)).isEqualTo(1.0);
        assertThat(TimeUtils.nanosToUnit(3_600_000_000_000.0, TimeUnit.HOURS)).isEqualTo(1.0);
        assertThat(TimeUtils.nanosToUnit(86_400_000_000_000.0, TimeUnit.DAYS)).isEqualTo(1.0);
    }

    @Test
    void microsToUnit() {
        // double
        assertThat(TimeUtils.microsToUnit(60.0, TimeUnit.NANOSECONDS)).isEqualTo(60_000.0);
        assertThat(TimeUtils.microsToUnit(60_000.0, TimeUnit.MICROSECONDS)).isEqualTo(60_000.0);
        assertThat(TimeUtils.microsToUnit(60_000.0, TimeUnit.MILLISECONDS)).isEqualTo(60.0);
        assertThat(TimeUtils.microsToUnit(60_000_000.0, TimeUnit.SECONDS)).isEqualTo(60.0);
        assertThat(TimeUtils.microsToUnit(60_000_000.0, TimeUnit.MINUTES)).isEqualTo(1.0);
        assertThat(TimeUtils.microsToUnit(3_600_000_000.0, TimeUnit.HOURS)).isEqualTo(1.0);
        assertThat(TimeUtils.microsToUnit(86_400_000_000.0, TimeUnit.DAYS)).isEqualTo(1.0);
    }

    @Test
    void millisToUnit() {
        // double
        assertThat(TimeUtils.millisToUnit(60_000.0, TimeUnit.NANOSECONDS)).isEqualTo(60_000_000_000.0);
        assertThat(TimeUtils.millisToUnit(60_000.0, TimeUnit.MICROSECONDS)).isEqualTo(60_000_000.0);
        assertThat(TimeUtils.millisToUnit(60_000.0, TimeUnit.MILLISECONDS)).isEqualTo(60_000.0);
        assertThat(TimeUtils.millisToUnit(60_000.0, TimeUnit.SECONDS)).isEqualTo(60.0);
        assertThat(TimeUtils.millisToUnit(60_000.0, TimeUnit.MINUTES)).isEqualTo(1.0);
        assertThat(TimeUtils.millisToUnit(3_600_000.0, TimeUnit.HOURS)).isEqualTo(1.0);
        assertThat(TimeUtils.millisToUnit(86_400_000.0, TimeUnit.DAYS)).isEqualTo(1.0);

        // long
        assertThat(TimeUtils.millisToUnit(60_000, TimeUnit.NANOSECONDS)).isEqualTo(60_000_000_000L);
        assertThat(TimeUtils.millisToUnit(60_000, TimeUnit.MICROSECONDS)).isEqualTo(60_000_000);
        assertThat(TimeUtils.millisToUnit(60_000, TimeUnit.MILLISECONDS)).isEqualTo(60_000);
        assertThat(TimeUtils.millisToUnit(60_000, TimeUnit.SECONDS)).isEqualTo(60);
        assertThat(TimeUtils.millisToUnit(60_000, TimeUnit.MINUTES)).isEqualTo(1);
        assertThat(TimeUtils.millisToUnit(3_600_000, TimeUnit.HOURS)).isEqualTo(1);
        assertThat(TimeUtils.millisToUnit(86_400_000, TimeUnit.DAYS)).isEqualTo(1);
    }

    @Test
    void secondsToUnit() {
        // double
        assertThat(TimeUtils.secondsToUnit(60.0, TimeUnit.NANOSECONDS)).isEqualTo(60_000_000_000.0);
        assertThat(TimeUtils.secondsToUnit(60.0, TimeUnit.MICROSECONDS)).isEqualTo(60_000_000.0);
        assertThat(TimeUtils.secondsToUnit(60.0, TimeUnit.MILLISECONDS)).isEqualTo(60_000.0);
        assertThat(TimeUtils.secondsToUnit(60.0, TimeUnit.SECONDS)).isEqualTo(60.0);
        assertThat(TimeUtils.secondsToUnit(60.0, TimeUnit.MINUTES)).isEqualTo(1.0);
        assertThat(TimeUtils.secondsToUnit(3_600.0, TimeUnit.HOURS)).isEqualTo(1.0);
        assertThat(TimeUtils.secondsToUnit(86_400.0, TimeUnit.DAYS)).isEqualTo(1.0);
    }

    @Test
    void minutesToUnit() {
        // double
        assertThat(TimeUtils.minutesToUnit(1.0, TimeUnit.NANOSECONDS)).isEqualTo(60_000_000_000.0);
        assertThat(TimeUtils.minutesToUnit(1.0, TimeUnit.MICROSECONDS)).isEqualTo(60_000_000.0);
        assertThat(TimeUtils.minutesToUnit(1.0, TimeUnit.MILLISECONDS)).isEqualTo(60_000.0);
        assertThat(TimeUtils.minutesToUnit(1.0, TimeUnit.SECONDS)).isEqualTo(60.0);
        assertThat(TimeUtils.minutesToUnit(1.0, TimeUnit.MINUTES)).isEqualTo(1.0);
        assertThat(TimeUtils.minutesToUnit(60.0, TimeUnit.HOURS)).isEqualTo(1.0);
        assertThat(TimeUtils.minutesToUnit(1_440.0, TimeUnit.DAYS)).isEqualTo(1.0);
    }

    @Test
    void hoursToUnit() {
        // double
        assertThat(TimeUtils.hoursToUnit(1.0, TimeUnit.NANOSECONDS)).isEqualTo(3_600_000_000_000.0);
        assertThat(TimeUtils.hoursToUnit(1.0, TimeUnit.MICROSECONDS)).isEqualTo(3_600_000_000.0);
        assertThat(TimeUtils.hoursToUnit(1.0, TimeUnit.MILLISECONDS)).isEqualTo(3_600_000.0);
        assertThat(TimeUtils.hoursToUnit(1.0, TimeUnit.SECONDS)).isEqualTo(3_600.0);
        assertThat(TimeUtils.hoursToUnit(1.0, TimeUnit.MINUTES)).isEqualTo(60.0);
        assertThat(TimeUtils.hoursToUnit(1.0, TimeUnit.HOURS)).isEqualTo(1.0);
        assertThat(TimeUtils.hoursToUnit(24.0, TimeUnit.DAYS)).isEqualTo(1.0);
    }

    @Test
    void daysToUnit() {
        // double
        assertThat(TimeUtils.daysToUnit(1.0, TimeUnit.NANOSECONDS)).isEqualTo(86_400_000_000_000.0);
        assertThat(TimeUtils.daysToUnit(1.0, TimeUnit.MICROSECONDS)).isEqualTo(86_400_000_000.0);
        assertThat(TimeUtils.daysToUnit(1.0, TimeUnit.MILLISECONDS)).isEqualTo(86_400_000.0);
        assertThat(TimeUtils.daysToUnit(1.0, TimeUnit.SECONDS)).isEqualTo(86_400.0);
        assertThat(TimeUtils.daysToUnit(1.0, TimeUnit.MINUTES)).isEqualTo(1_440.0);
        assertThat(TimeUtils.daysToUnit(1.0, TimeUnit.HOURS)).isEqualTo(24.0);
        assertThat(TimeUtils.daysToUnit(1.0, TimeUnit.DAYS)).isEqualTo(1.0);
    }

}
