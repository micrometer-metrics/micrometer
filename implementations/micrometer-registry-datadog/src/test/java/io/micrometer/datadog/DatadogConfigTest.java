/**
 * Copyright 2019 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.datadog;

import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatadogConfigTest {

    @Test
    void returnsDefaultPrefix() {
        assertThat(new TestDatadogConfig("value").prefix()).isEqualTo("datadog");
    }

    @Test
    void returnsApiKey() {
        String expectedValue = "value";
        assertThat(new TestDatadogConfig(expectedValue).apiKey()).isEqualTo(expectedValue);
    }

    @Test
    void throwsConfigurationExceptionOnNullValue() {
        assertThrows(MissingRequiredConfigurationException.class, () -> new TestDatadogConfig(null).apiKey());
    }

    @Test
    void returnsApplicationKey() {
        String expectedValue = "value";
        assertThat(new TestDatadogConfig(expectedValue).applicationKey()).isEqualTo(expectedValue);
    }

    @Test
    void returnsHostTag() {
        String expectedValue = "value";
        assertThat(new TestDatadogConfig(expectedValue).hostTag()).isEqualTo(expectedValue);
    }

    @Test
    void defaultsHostTagWhenMissing() {
        assertThat(new TestDatadogConfig(null).hostTag()).isEqualTo("instance");
    }

    @Test
    void returnsUri() {
        String expectedValue = "value";
        assertThat(new TestDatadogConfig(expectedValue).uri()).isEqualTo(expectedValue);
    }

    @Test
    void defaultsUriWhenMissing() {
        assertThat(new TestDatadogConfig(null).uri()).isEqualTo("https://app.datadoghq.com");
    }

    @Test
    void returnsTrueOnNullDescription() {
        assertThat(new TestDatadogConfig(null).descriptions()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void returnsExpectedBooleanDescription(String value) {
        assertThat(new TestDatadogConfig(value).descriptions()).isEqualTo(Boolean.valueOf(value));
    }

    private static class TestDatadogConfig implements DatadogConfig {

        private final String value;

        private TestDatadogConfig(String value) {
            this.value = value;
        }

        @Override
        public String get(String key) {
            return value;
        }

    }
}
