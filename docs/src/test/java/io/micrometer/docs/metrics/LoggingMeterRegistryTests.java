/*
 * Copyright 2026 VMware, Inc.
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
package io.micrometer.docs.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.logging.LoggingMeterRegistry;
import io.micrometer.core.instrument.logging.LoggingRegistryConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Doc examples for {@link LoggingMeterRegistry}.
 *
 * @author Jonatan Ivanov
 */
class LoggingMeterRegistryTests {

    @Test
    void setup() {
        // tag::setup[]
        MeterRegistry registry = new LoggingMeterRegistry();
        registry.counter("my.counter").increment();
        // end::setup[]
        registry.close();
    }

    @Test
    void config() {
        // tag::config[]
        LoggingRegistryConfig config = new LoggingRegistryConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public Duration step() {
                return Duration.ofSeconds(30);
            }

            @Override
            public boolean logInactive() {
                return true;
            }
        };

        MeterRegistry registry = new LoggingMeterRegistry(config, Clock.SYSTEM);
        // end::config[]

        assertThat(config.step()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.logInactive()).isTrue();
        registry.close();
    }

    @Test
    void propertiesConfig() {
        // tag::properties_config[]
        Map<String, String> properties = Map.of("logging.step", "30s", "logging.logInactive", "true");
        LoggingRegistryConfig config = properties::get;
        // end::properties_config[]

        assertThat(config.step()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.logInactive()).isTrue();
    }

    @Test
    void customOutput() {
        // @formatter:off
        // tag::custom_output[]
        MeterRegistry registry = LoggingMeterRegistry.builder(LoggingRegistryConfig.DEFAULT)
            .loggingSink(System.out::println)
            .build();
        // end::custom_output[]
        // @formatter:on
        registry.close();
    }

    @Test
    void sampleOutput() {
        List<String> logs = new ArrayList<>();
        MeterRegistry registry = LoggingMeterRegistry.builder(LoggingRegistryConfig.DEFAULT)
            .clock(new MockClock())
            .loggingSink(logs::add)
            .build();

        // tag::sample_output[]
        Gauge.builder("demo.gauge", Math::random).tag("app", "demo").register(registry);
        Counter.builder("demo.counter").tag("app", "demo").register(registry).increment();
        Timer.builder("demo.timer").tag("app", "demo").register(registry).record(Duration.ofMillis(42));
        // end::sample_output[]

        registry.close();

        assertThat(logs).hasSize(3);
        assertThat(logs.toString()).contains("demo.counter{app=demo}", "demo.gauge{app=demo}", "demo.timer{app=demo}");
    }

}
