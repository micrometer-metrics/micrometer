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
package io.micrometer.azuremonitor;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AzureMonitorConfigTest {

    private final Map<String, String> props = new HashMap<>();

    private final AzureMonitorConfig config = props::get;

    @Test
    void validWithInstrumentationKey() {
        props.put("azuremonitor.instrumentationKey", "key");

        assertThat(config.validate().isValid()).isTrue();
    }

    @Test
    void validWithConnectionString() {
        props.put("azuremonitor.connectionString", "secret");

        assertThat(config.validate().isValid()).isTrue();
    }

    @Test
    void connectionStringUsesInstrumentationKeyIfUnset() {
        props.put("azuremonitor.instrumentationKey", "key");

        assertThat(config.connectionString()).isEqualTo("InstrumentationKey=key");
    }

    @Test
    void connectionStringIgnoresInstrumentationKeyIfSet() {
        props.put("azuremonitor.instrumentationKey", "key");
        props.put("azuremonitor.connectionString", "InstrumentationKey=another");

        assertThat(config.connectionString()).isEqualTo("InstrumentationKey=another");
    }

}
