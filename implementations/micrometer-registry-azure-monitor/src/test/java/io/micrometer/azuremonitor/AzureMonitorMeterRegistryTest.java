/**
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.azuremonitor;

import com.microsoft.applicationinsights.TelemetryConfiguration;
import io.micrometer.core.instrument.config.validate.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link AzureMonitorMeterRegistry}.
 */
class AzureMonitorMeterRegistryTest {

    @Test
    void useTelemetryConfigInstrumentationKeyWhenSet() {
        TelemetryConfiguration telemetryConfiguration = TelemetryConfiguration.createDefault();
        telemetryConfiguration.setInstrumentationKey("fake");
        AzureMonitorMeterRegistry.builder(new AzureMonitorConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String instrumentationKey() {
                return "other";
            }
        }).telemetryConfiguration(telemetryConfiguration).build();
        assertThat(telemetryConfiguration.getInstrumentationKey()).isEqualTo("fake");
    }

    @Test
    void failWhenTelemetryConfigInstrumentationKeyIsUnsetAndConfigInstrumentationKeyIsNull() {
        TelemetryConfiguration telemetryConfiguration = TelemetryConfiguration.createDefault();
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> AzureMonitorMeterRegistry.builder(key -> null)
                        .telemetryConfiguration(telemetryConfiguration).build());
    }
}
