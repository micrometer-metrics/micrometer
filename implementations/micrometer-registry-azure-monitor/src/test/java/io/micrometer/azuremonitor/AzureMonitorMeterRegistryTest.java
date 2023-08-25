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

import com.microsoft.applicationinsights.TelemetryConfiguration;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.validate.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link AzureMonitorMeterRegistry}.
 *
 * @author Tommy Ludwig
 * @author Johnny Lim
 */
class AzureMonitorMeterRegistryTest {

    private final AzureMonitorConfig config = new AzureMonitorConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public String connectionString() {
            return "InstrumentationKey=connectionStringKey";
        }
    };

    private final AzureMonitorConfig configWithInstrumentationKey = new AzureMonitorConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @SuppressWarnings("deprecation")
        @Override
        public String instrumentationKey() {
            return "myInstrumentationKey";
        }
    };

    private final MockClock clock = new MockClock();

    private final AzureMonitorMeterRegistry registry = new AzureMonitorMeterRegistry(config, clock);

    @Test
    void useTelemetryConfigConnectionStringWhenSet() {
        TelemetryConfiguration telemetryConfiguration = TelemetryConfiguration.createDefault();
        telemetryConfiguration.setConnectionString("InstrumentationKey=direct");
        AzureMonitorMeterRegistry.builder(config).telemetryConfiguration(telemetryConfiguration).build();
        assertThat(telemetryConfiguration.getConnectionString()).isEqualTo("InstrumentationKey=direct");
        assertThat(telemetryConfiguration.getInstrumentationKey()).isEqualTo("direct");
    }

    @Test
    void configInstrumentationKeyStillSetsTelemetryConfigInstrumentationKey() {
        TelemetryConfiguration telemetryConfiguration = TelemetryConfiguration.createDefault();
        AzureMonitorMeterRegistry.builder(configWithInstrumentationKey)
            .telemetryConfiguration(telemetryConfiguration)
            .build();
        assertThat(telemetryConfiguration.getConnectionString()).isEqualTo("InstrumentationKey=myInstrumentationKey");
        assertThat(telemetryConfiguration.getInstrumentationKey()).isEqualTo("myInstrumentationKey");
    }

    @Test
    void failWhenTelemetryConfigInstrumentationKeyIsUnsetAndConfigInstrumentationKeyIsNull() {
        TelemetryConfiguration telemetryConfiguration = TelemetryConfiguration.createDefault();
        assertThatExceptionOfType(ValidationException.class)
            .isThrownBy(() -> AzureMonitorMeterRegistry.builder(key -> null)
                .telemetryConfiguration(telemetryConfiguration)
                .build());
    }

    @Test
    void trackTimer() {
        Timer timer = Timer.builder("my.timer").register(registry);
        timer.record(Duration.ofSeconds(1));
        clock.add(config.step());
        assertThat(registry.trackTimer(timer)).hasSize(1);
    }

    @Test
    void trackTimerWhenCountIsZeroShouldReturnEmpty() {
        Timer timer = Timer.builder("my.timer").register(registry);
        clock.add(config.step());
        assertThat(registry.trackTimer(timer)).isEmpty();
    }

    @Test
    void trackFunctionTimer() {
        FunctionTimer functionTimer = FunctionTimer
            .builder("my.function.timer", 1d, Number::longValue, Number::doubleValue, TimeUnit.MILLISECONDS)
            .register(registry);
        clock.add(config.step());
        assertThat(registry.trackFunctionTimer(functionTimer)).hasSize(1);
    }

    @Test
    void trackFunctionTimerWhenCountIsZeroShouldReturnEmpty() {
        FunctionTimer functionTimer = FunctionTimer
            .builder("my.function.timer", 0d, Number::longValue, Number::doubleValue, TimeUnit.MILLISECONDS)
            .register(registry);
        clock.add(config.step());
        assertThat(registry.trackFunctionTimer(functionTimer)).isEmpty();
    }

    @Test
    void trackDistributionSummary() {
        DistributionSummary distributionSummary = DistributionSummary.builder("my.distribution.summary")
            .register(registry);
        distributionSummary.record(1d);
        clock.add(config.step());
        assertThat(registry.trackDistributionSummary(distributionSummary)).hasSize(1);
    }

    @Test
    void trackDistributionSummaryWhenCountIsZeroShouldReturnEmpty() {
        DistributionSummary distributionSummary = DistributionSummary.builder("my.distribution.summary")
            .register(registry);
        clock.add(config.step());
        assertThat(registry.trackDistributionSummary(distributionSummary)).isEmpty();
    }

}
