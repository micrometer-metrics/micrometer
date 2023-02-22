/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static io.micrometer.core.instrument.config.validate.DurationValidator.validate;
import static org.assertj.core.api.Assertions.assertThat;

class DurationValidatorTest {

    @Test
    void parseHumanReadableUnits() {
        assertThat(validate("dur", "5ns").get()).isEqualByComparingTo(Duration.ofNanos(5));
        assertThat(validate("dur", "700ms").get()).isEqualByComparingTo(Duration.ofMillis(700));
        assertThat(validate("dur", "1us").get()).isEqualByComparingTo(Duration.ofNanos(1000));
        assertThat(validate("dur", "1s").get()).isEqualByComparingTo(Duration.ofSeconds(1));
        assertThat(validate("dur", "10m").get()).isEqualByComparingTo(Duration.ofMinutes(10));
        assertThat(validate("dur", "13h").get()).isEqualByComparingTo(Duration.ofHours(13));
        assertThat(validate("dur", "5d").get()).isEqualByComparingTo(Duration.ofDays(5));

        assertThat(validate("dur", "7,000 ms").get()).isEqualByComparingTo(Duration.ofMillis(7000));
        assertThat(validate("dur", "7_000ms ").get()).isEqualByComparingTo(Duration.ofMillis(7000));

        assertThat(validate("dur", "1.1s").get()).isEqualByComparingTo(Duration.ofMillis(1100));

        assertThat(validate("dur", "+1.1s").get()).isEqualByComparingTo(Duration.ofMillis(1100));
    }

    @Test
    void parseIso8601Duration() {
        assertThat(validate("dur", "PT20.345S").get()).isEqualByComparingTo(Duration.ofMillis(20_345));
        assertThat(validate("dur", "PT15M").get()).isEqualByComparingTo(Duration.ofMinutes(15));
        assertThat(validate("dur", "+PT15M").get()).isEqualByComparingTo(Duration.ofMinutes(15));
        assertThat(validate("dur", "PT10H").get()).isEqualByComparingTo(Duration.ofHours(10));
        assertThat(validate("dur", "P2D").get()).isEqualByComparingTo(Duration.ofDays(2));
        assertThat(validate("dur", "P2DT3H4M").get())
            .isEqualByComparingTo(Duration.ofDays(2).plus(Duration.ofHours(3)).plus(Duration.ofMinutes(4)));
    }

}
