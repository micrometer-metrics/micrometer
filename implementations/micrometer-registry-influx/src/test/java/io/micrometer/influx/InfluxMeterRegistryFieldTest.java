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
package io.micrometer.influx;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Tests for {@link io.micrometer.influx.InfluxMeterRegistry.Field}.
 *
 * @author Niclas Thall
 * @author Jon Schneider
 * @author Johnny Lim
 */
class InfluxMeterRegistryFieldTest {

    private final Locale originalLocale = Locale.getDefault();

    @AfterEach
    void cleanUp() {
        Locale.setDefault(this.originalLocale);
    }

    @Test
    void toStringWithEnglishLocale() {
        Locale.setDefault(Locale.ENGLISH);

        InfluxMeterRegistry.Field field = new InfluxMeterRegistry.Field("value", 0.01);

        assertThat(field.toString()).isEqualTo("value=0.01");
    }

    @Test
    void toStringWithEnglishLocaleWithLargerResolution() {
        Locale.setDefault(Locale.ENGLISH);

        InfluxMeterRegistry.Field field = new InfluxMeterRegistry.Field("value", 0.0000009);

        assertThat(field.toString()).isEqualTo("value=0.000001");
    }

    @Test
    void toStringWithSwedishLocale() {
        Locale.setDefault(new Locale("sv", "SE"));

        InfluxMeterRegistry.Field field = new InfluxMeterRegistry.Field("value", 0.01);

        assertThat(field.toString()).isEqualTo("value=0.01");
    }

    @Test
    void timeCannotBeAFieldKey() {
        assertThat(catchThrowable(() -> new InfluxMeterRegistry.Field("time", 1.0)))
            .isInstanceOf(IllegalArgumentException.class);
    }

}
