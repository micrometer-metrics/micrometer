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
package io.micrometer.dynatrace;

import java.lang.reflect.Field;
import java.util.Set;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.ipc.http.HttpResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DynatraceMeterRegistry}.
 *
 * @author Johnny Lim
 */
class DynatraceMeterRegistryTest {

    @Test
    void constructorWhenUriIsMissingShouldThrowMissingRequiredConfigurationException() {
        assertThatThrownBy(() -> new DynatraceMeterRegistry(new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String deviceId() {
                return "deviceId";
            }

            @Override
            public String apiToken() {
                return "apiToken";
            }
        }, Clock.SYSTEM))
            .isExactlyInstanceOf(MissingRequiredConfigurationException.class)
            .hasMessage("uri must be set to report metrics to Dynatrace");
    }

    @Test
    void constructorWhenDeviceIdIsMissingShouldThrowMissingRequiredConfigurationException() {
        assertThatThrownBy(() -> new DynatraceMeterRegistry(new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String uri() {
                return "uri";
            }

            @Override
            public String apiToken() {
                return "apiToken";
            }
        }, Clock.SYSTEM))
            .isExactlyInstanceOf(MissingRequiredConfigurationException.class)
            .hasMessage("deviceId must be set to report metrics to Dynatrace");
    }

    @Test
    void constructorWhenApiTokenIsMissingShouldThrowMissingRequiredConfigurationException() {
        assertThatThrownBy(() -> new DynatraceMeterRegistry(new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String uri() {
                return "uri";
            }

            @Override
            public String deviceId() {
                return "deviceId";
            }
        }, Clock.SYSTEM))
            .isExactlyInstanceOf(MissingRequiredConfigurationException.class)
            .hasMessage("apiToken must be set to report metrics to Dynatrace");
    }

    @Test
    void putCustomMetricOnSuccessShouldAddMetricIdToCreatedCustomMetrics() throws NoSuchFieldException, IllegalAccessException {
        DynatraceConfig config = new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String uri() {
                return "http://uri";
            }

            @Override
            public String deviceId() {
                return "deviceId";
            }

            @Override
            public String apiToken() {
                return "apiToken";
            }
        };
        DynatraceMeterRegistry registry = DynatraceMeterRegistry.builder(config)
                .httpPushHandler((url, request) -> new HttpResponse(200, null))
                .build();

        Field createdCustomMetricsField = DynatraceMeterRegistry.class.getDeclaredField("createdCustomMetrics");
        createdCustomMetricsField.setAccessible(true);
        Set<String> createdCustomMetrics = (Set<String>) createdCustomMetricsField.get(registry);
        assertThat(createdCustomMetrics).isEmpty();

        DynatraceMetricDefinition customMetric = new DynatraceMetricDefinition("metricId", null, null, null, new String[] { "type" });
        registry.putCustomMetric(customMetric);
        assertThat(createdCustomMetrics).containsExactly("metricId");
    }

}
