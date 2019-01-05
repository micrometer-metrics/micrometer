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

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.ipc.http.HttpSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DynatraceMeterRegistry}.
 *
 * @author Johnny Lim
 */
class DynatraceMeterRegistryTest {

    private final DynatraceMeterRegistry meterRegistry = createMeterRegistry();

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
        Field createdCustomMetricsField = DynatraceMeterRegistry.class.getDeclaredField("createdCustomMetrics");
        createdCustomMetricsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> createdCustomMetrics = (Set<String>) createdCustomMetricsField.get(meterRegistry);
        assertThat(createdCustomMetrics).isEmpty();

        DynatraceMetricDefinition customMetric = new DynatraceMetricDefinition("metricId", null, null, null, new String[]{"type"});
        meterRegistry.putCustomMetric(customMetric);
        assertThat(createdCustomMetrics).containsExactly("metricId");
    }

    @Test
    void writeMeterWithGauge() {
        meterRegistry.gauge("my.gauge", 1d);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeMeter(gauge)).hasSize(1);
    }

    @Test
    void writeMeterWithGaugeShouldDropNanValue() {
        meterRegistry.gauge("my.gauge", Double.NaN);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeMeter(gauge)).isEmpty();
    }

    @Test
    void writeMeterWithGaugeShouldDropInfiniteValues() {
        meterRegistry.gauge("my.gauge", Double.POSITIVE_INFINITY);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeMeter(gauge)).isEmpty();

        meterRegistry.gauge("my.gauge", Double.NEGATIVE_INFINITY);
        gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeMeter(gauge)).isEmpty();
    }

    @Test
    void writeMeterWithTimeGauge() {
        AtomicReference<Double> obj = new AtomicReference<>(1d);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeMeter(timeGauge)).hasSize(1);
    }

    @Test
    void writeMeterWithTimeGaugeShouldDropNanValue() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.NaN);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeMeter(timeGauge)).isEmpty();
    }

    @Test
    void writeMeterWithTimeGaugeShouldDropInfiniteValues() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.POSITIVE_INFINITY);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeMeter(timeGauge)).isEmpty();

        obj = new AtomicReference<>(Double.NEGATIVE_INFINITY);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeMeter(timeGauge)).isEmpty();
    }

    private DynatraceMeterRegistry createMeterRegistry() {
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
        return DynatraceMeterRegistry.builder(config)
            .httpClient(request -> new HttpSender.Response(200, null))
            .build();
    }

}
