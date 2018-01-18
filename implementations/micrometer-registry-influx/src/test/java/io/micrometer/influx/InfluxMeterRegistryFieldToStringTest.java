/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.influx;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MockClock;

public class InfluxMeterRegistryFieldToStringTest {

    private Locale originalLocale;

    @BeforeEach
    public void setUp() {
        this.originalLocale = Locale.getDefault();
    }

    @AfterEach
    public void cleanUp() {
        Locale.setDefault(this.originalLocale);
    }

	@Test
	public void testWithEnglishLocale() {
		Locale.setDefault(Locale.ENGLISH);
		InfluxMeterRegistry instance = new InfluxMeterRegistry(k -> null, new MockClock());

		InfluxMeterRegistry.Field field = instance.new Field("value", 0.01);

		assertThat(field.toString()).isEqualTo("value=0.01");
	}

	@Test
	public void testWithEnglishLocaleWithLargerResolution() {
		Locale.setDefault(Locale.ENGLISH);
		InfluxMeterRegistry instance = new InfluxMeterRegistry(k -> null, new MockClock());

		InfluxMeterRegistry.Field field = instance.new Field("value", 0.00009);

		assertThat(field.toString()).isEqualTo("value=0.0001");
	}

	@Test
	public void testWithSwedishLocale() {
		Locale.setDefault(new Locale("sv", "SE"));

		InfluxMeterRegistry instance = new InfluxMeterRegistry(k -> null, new MockClock());
		InfluxMeterRegistry.Field field = instance.new Field("value", 0.01);

		assertThat(field.toString()).isEqualTo("value=0.01");
	}
}
