/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.dynatrace;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.ipc.http.HttpSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
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

    @Test
    void writeCustomMetrics() {
        meterRegistry.gauge("my.gauge", 1d);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        Stream<DynatraceMeterRegistry.DynatraceCustomMetric> series = meterRegistry.writeMeter(gauge);
        List<DynatraceTimeSeries> timeSeries = series
            .map(DynatraceMeterRegistry.DynatraceCustomMetric::getTimeSeries)
            .collect(Collectors.toList());
        List<DynatraceBatchedPayload> entries = meterRegistry.createPostMessages("my.type", timeSeries);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).metricCount).isEqualTo(1);
        assertThat(isJSONValid(entries.get(0).payload)).isEqualTo(true);
    }

    @Test
    void whenAllTsTooLargeEmptyMessageListReturned() {
        List<DynatraceBatchedPayload> messages = meterRegistry.createPostMessages("my.type", Collections.singletonList(createTimeSeriesWithDimensions(10_000)));
        assertThat(messages).isEmpty();
    }

    @Test
    void splitsWhenExactlyExceedingMaxByComma() {
        // comma needs to be considered when there is more than one time series
        List<DynatraceBatchedPayload> messages = meterRegistry.createPostMessages("my.type",
            // Max bytes: 15330 (excluding header/footer, 15360 with header/footer)
            Arrays.asList(createTimeSeriesWithDimensions(750), // 14861 bytes
                createTimeSeriesWithDimensions(23, "asdfg"), // 469 bytes (overflows due to comma)
                createTimeSeriesWithDimensions(750), // 14861 bytes
                createTimeSeriesWithDimensions(23, "asdf") // 468 bytes + comma
            ));
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).metricCount).isEqualTo(1);
        assertThat(messages.get(1).metricCount).isEqualTo(1);
        assertThat(messages.get(2).metricCount).isEqualTo(2);
        assertThat(messages.get(2).payload.getBytes(UTF_8).length).isEqualTo(15360);
        assertThat(messages.stream().map(message -> message.payload).allMatch(DynatraceMeterRegistryTest::isJSONValid)).isTrue();
    }

    @Test
    void countsPreviousAndNextComma() {
        List<DynatraceBatchedPayload> messages = meterRegistry.createPostMessages("my.type",
            // Max bytes: 15330 (excluding header/footer, 15360 with header/footer)
            Arrays.asList(createTimeSeriesWithDimensions(750), // 14861 bytes
                createTimeSeriesWithDimensions(10, "asdf"), // 234 bytes + comma
                createTimeSeriesWithDimensions(10, "asdf") // 234 bytes + comma = 15331 bytes (overflow)
            ));
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).metricCount).isEqualTo(2);
        assertThat(messages.get(1).metricCount).isEqualTo(1);
        assertThat(messages.stream().map(message -> message.payload).allMatch(DynatraceMeterRegistryTest::isJSONValid)).isTrue();
    }

    private DynatraceTimeSeries createTimeSeriesWithDimensions(int numberOfDimensions) {
        return createTimeSeriesWithDimensions(numberOfDimensions, "some.metric");
    }
    private DynatraceTimeSeries createTimeSeriesWithDimensions(int numberOfDimensions, String metricId) {
        return new DynatraceTimeSeries(metricId, System.currentTimeMillis(), 1.23, createDimensionsMap(numberOfDimensions));
    }
    private Map<String, String> createDimensionsMap(int numberOfDimensions) {
        Map<String, String> map = new HashMap<>();
        IntStream.range(0, numberOfDimensions).forEach(i -> map.put("key" + i, "value" + i));
        return map;
    }

    private DynatraceMeterRegistry createMeterRegistry() {
        DynatraceConfig config = new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String uri() {
                return "http://localhost";
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

    private static boolean isJSONValid(String jsonInString ) {
        try {
            final ObjectMapper mapper = new ObjectMapper();
            mapper.readTree(jsonInString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
