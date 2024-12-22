/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.registry.otlp;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.ipc.http.HttpSender;
import io.opentelemetry.proto.metrics.v1.HistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.*;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

/**
 * Tests for {@link OtlpMeterRegistry}.
 *
 * @author Tommy Ludwig
 * @author Johnny Lim
 */
abstract class OtlpMeterRegistryTest {

    protected static final String METER_NAME = "test.meter";

    protected static final String METER_DESCRIPTION = "Sample meter description";

    protected static final Tag meterTag = Tag.of("key", "value");

    protected MockClock clock;

    protected OtlpMeterRegistry registry;

    private HttpSender mockHttpSender;

    abstract OtlpConfig otlpConfig();

    @BeforeEach
    void setUp() {
        this.clock = new MockClock();
        this.mockHttpSender = mock(HttpSender.class);
        this.registry = new OtlpMeterRegistry(otlpConfig(), this.clock, this.mockHttpSender);
    }

    // If the service.name was not specified, SDKs MUST fallback to 'unknown_service'
    @Test
    void unknownServiceByDefault() {
        assertThat(registry.getResourceAttributes())
            .contains(OtlpMeterRegistry.createKeyValue("service.name", "unknown_service"));
    }

    @Test
    void setServiceNameOverrideMethod() {
        registry = new OtlpMeterRegistry(new OtlpConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public Map<String, String> resourceAttributes() {
                return Collections.singletonMap("service.name", "myService");
            }
        }, Clock.SYSTEM);

        assertThat(registry.getResourceAttributes())
            .contains(OtlpMeterRegistry.createKeyValue("service.name", "myService"));
    }

    @Test
    void reservedResourceAttributesAreKept() {
        registry = new OtlpMeterRegistry(new OtlpConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public Map<String, String> resourceAttributes() {
                return Map.of("telemetry.sdk.language", "no", "telemetry.sdk.version", "no", "telemetry.sdk.name",
                        "no");
            }
        }, Clock.SYSTEM);

        assertThat(registry.getResourceAttributes())
            .noneMatch(keyValue -> keyValue.getValue().getStringValue().equals("no"));
    }

    @Test
    void setResourceAttributesAsString() throws IOException {
        Properties propertiesConfig = new Properties();
        propertiesConfig.load(this.getClass().getResourceAsStream("/otlp-config.properties"));
        registry = new OtlpMeterRegistry(key -> (String) propertiesConfig.get(key), Clock.SYSTEM);
        assertThat(registry.getResourceAttributes()).contains(OtlpMeterRegistry.createKeyValue("key1", "value1"),
                OtlpMeterRegistry.createKeyValue("key2", "value2"));
    }

    @Test
    void setResourceAttributesFromEnvironmentVariables() throws Exception {
        withEnvironmentVariables("OTEL_RESOURCE_ATTRIBUTES", "a=1,b=2", "OTEL_SERVICE_NAME", "my-service")
            .execute(() -> {
                assertThat(registry.getResourceAttributes()).contains(OtlpMeterRegistry.createKeyValue("a", "1"),
                        OtlpMeterRegistry.createKeyValue("b", "2"),
                        OtlpMeterRegistry.createKeyValue("service.name", "my-service"));
            });
    }

    @Test
    void timeGauge() {
        TimeGauge timeGauge = TimeGauge.builder("gauge.time", this, TimeUnit.MICROSECONDS, o -> 24).register(registry);

        assertThat(writeToMetric(timeGauge).toString())
            .isEqualTo("name: \"gauge.time\"\n" + "unit: \"milliseconds\"\n" + "gauge {\n" + "  data_points {\n"
                    + "    time_unix_nano: 1000000\n" + "    as_double: 0.024\n" + "  }\n" + "}\n");
    }

    @Issue("#5577")
    @Test
    void httpHeaders() throws Throwable {
        HttpSender.Request.Builder builder = HttpSender.Request.build(otlpConfig().url(), this.mockHttpSender);
        when(mockHttpSender.post(otlpConfig().url())).thenReturn(builder);

        when(mockHttpSender.send(isA(HttpSender.Request.class))).thenReturn(new HttpSender.Response(200, ""));

        writeToMetric(TimeGauge.builder("gauge.time", this, TimeUnit.MICROSECONDS, o -> 24).register(registry));
        registry.publish();

        verify(this.mockHttpSender).send(assertArg(request -> {
            assertThat(request.getRequestHeaders().get("User-Agent")).startsWith("Micrometer-OTLP-Exporter-Java");
            assertThat(request.getRequestHeaders()).containsEntry("Content-Type", "application/x-protobuf");
        }));
    }

    @Test
    void distributionWithPercentileShouldWriteSummary() {
        Timer timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentiles(0.5, 0.9)
            .register(registry);

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentiles(0.5, 0.9)
            .register(registry);

        assertThat(writeToMetric(timer).getDataCase().getNumber()).isEqualTo(Metric.DataCase.SUMMARY.getNumber());
        assertThat(writeToMetric(ds).getDataCase().getNumber()).isEqualTo(Metric.DataCase.SUMMARY.getNumber());
    }

    @SuppressWarnings("deprecation")
    @Test
    void multipleMetricsWithSameMetaDataShouldBeSingleMetric() {
        Tags firstTag = Tags.of("key", "first");
        Tags secondTag = Tags.of("key", "second");

        Gauge.builder("test.gauge", () -> 1).description("description").tags(firstTag).register(registry);
        Gauge.builder("test.gauge", () -> 1).description("description").tags(secondTag).register(registry);

        Counter.builder("test.counter").description("description").tags(firstTag).register(registry);
        Counter.builder("test.counter").description("description").tags(secondTag).register(registry);

        Timer.builder("test.timer").description("description").tags(firstTag).register(registry);
        Timer.builder("test.timer").description("description").tags(secondTag).register(registry);

        List<Metric> metrics = writeAllMeters();
        assertThat(metrics).hasSize(3);

        assertThat(metrics).filteredOn(Metric::hasGauge).hasSize(1).first().satisfies(metric -> {
            assertThat(metric.getDescription()).isEqualTo("description");
            assertThat(metric.getGauge().getDataPointsCount()).isEqualTo(2);
        });

        assertThat(metrics).filteredOn(Metric::hasSum).hasSize(1).first().satisfies(metric -> {
            assertThat(metric.getDescription()).isEqualTo("description");
            assertThat(metric.getSum().getDataPointsCount()).isEqualTo(2);
            assertThat(metric.getSum().getAggregationTemporality())
                .isEqualTo(AggregationTemporality.toOtlpAggregationTemporality(otlpConfig().aggregationTemporality()));
        });

        assertThat(metrics).filteredOn(Metric::hasHistogram).hasSize(1).first().satisfies(metric -> {
            assertThat(metric.getDescription()).isEqualTo("description");
            assertThat(metric.getHistogram().getDataPointsCount()).isEqualTo(2);
            assertThat(metric.getHistogram().getAggregationTemporality())
                .isEqualTo(AggregationTemporality.toOtlpAggregationTemporality(otlpConfig().aggregationTemporality()));
        });
    }

    @Test
    void metricsWithDifferentMetadataShouldBeMultipleMetrics() {
        Tags firstTag = Tags.of("key", "first");
        Tags secondTag = Tags.of("key", "second");

        final String description1 = "description1";
        final String description2 = "description2";
        Gauge.builder("test.gauge", () -> 1).description(description1).tags(firstTag).register(registry);
        Gauge.builder("test.gauge", () -> 1).description(description2).tags(secondTag).register(registry);

        Counter.builder("test.counter").description(description1).tags(firstTag).register(registry);
        Counter.builder("test.counter").baseUnit("xyz").description(description1).tags(secondTag).register(registry);

        Timer.builder("test.timer").description(description1).tags(firstTag).register(registry);
        Timer.builder("test.timer").description(description2).tags(secondTag).register(registry);

        List<Metric> metrics = writeAllMeters();
        assertThat(metrics).hasSize(6);
        assertThat(metrics).filteredOn(Metric::hasGauge).hasSize(2).satisfiesExactlyInAnyOrder(metric -> {
            assertThat(metric.getDescription()).isEqualTo(description1);
        }, metric -> {
            assertThat(metric.getDescription()).isEqualTo(description2);
        });

        assertThat(metrics).filteredOn(Metric::hasSum).hasSize(2).satisfiesExactlyInAnyOrder(metric -> {
            assertThat(metric.getUnit()).isEmpty();
        }, metric -> {
            assertThat(metric.getUnit()).isEqualTo("xyz");
        });

        assertThat(metrics).filteredOn(Metric::hasHistogram).hasSize(2).satisfiesExactlyInAnyOrder(metric -> {
            assertThat(metric.getDescription()).isEqualTo(description1);
        }, metric -> {
            assertThat(metric.getDescription()).isEqualTo(description2);
        });
    }

    @Test
    void distributionWithPercentileAndHistogramShouldWriteHistogramDataPoint() {
        Timer timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentiles(0.5, 0.9)
            .publishPercentileHistogram()
            .serviceLevelObjectives(Duration.ofMillis(1))
            .register(registry);

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentiles(0.5, 0.9)
            .publishPercentileHistogram()
            .serviceLevelObjectives(1.0)
            .register(registry);

        assertThat(writeToMetric(timer).getDataCase().getNumber()).isEqualTo(Metric.DataCase.HISTOGRAM.getNumber());
        assertThat(writeToMetric(ds).getDataCase().getNumber()).isEqualTo(Metric.DataCase.HISTOGRAM.getNumber());
    }

    @Test
    void distributionWithHistogramShouldWriteHistogramDataPoint() {
        Timer timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .serviceLevelObjectives(Duration.ofMillis(1))
            .register(registry);
        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .serviceLevelObjectives(1.0)
            .register(registry);

        assertThat(writeToMetric(timer).getDataCase().getNumber()).isEqualTo(Metric.DataCase.HISTOGRAM.getNumber());
        assertThat(writeToMetric(ds).getDataCase().getNumber()).isEqualTo(Metric.DataCase.HISTOGRAM.getNumber());
    }

    @Test
    void testGetSloWithPositiveInf() {
        DistributionStatisticConfig distributionStatisticConfig = DistributionStatisticConfig.builder()
            .percentilesHistogram(true)
            .build();

        assertThat(OtlpMeterRegistry.getSloWithPositiveInf(distributionStatisticConfig))
            .containsExactly(Double.POSITIVE_INFINITY);

        DistributionStatisticConfig distributionStatisticConfigWithSlo = DistributionStatisticConfig.builder()
            .serviceLevelObjectives(1, 10, 100)
            .build();
        assertThat(OtlpMeterRegistry.getSloWithPositiveInf(distributionStatisticConfigWithSlo))
            .contains(Double.POSITIVE_INFINITY);
        assertThat(OtlpMeterRegistry.getSloWithPositiveInf(distributionStatisticConfigWithSlo)).hasSize(4);

        DistributionStatisticConfig distributionStatisticConfigWithInf = DistributionStatisticConfig.builder()
            .serviceLevelObjectives(1, 10, 100, Double.POSITIVE_INFINITY)
            .build();
        assertThat(OtlpMeterRegistry.getSloWithPositiveInf(distributionStatisticConfigWithInf))
            .contains(Double.POSITIVE_INFINITY);
        assertThat(OtlpMeterRegistry.getSloWithPositiveInf(distributionStatisticConfigWithInf)).hasSize(4);
    }

    @Test
    abstract void testMetricsStartAndEndTime();

    protected Metric writeToMetric(Meter meter) {
        OtlpMetricConverter otlpMetricConverter = new OtlpMetricConverter(clock, otlpConfig().step(),
                registry.getBaseTimeUnit(), otlpConfig().aggregationTemporality(),
                registry.config().namingConvention());
        otlpMetricConverter.addMeter(meter);
        final List<Metric> metrics = otlpMetricConverter.getAllMetrics();
        return metrics.isEmpty() ? Metric.getDefaultInstance() : metrics.get(0);
    }

    protected List<Metric> writeAllMeters() {
        OtlpMetricConverter otlpMetricConverter = new OtlpMetricConverter(clock, otlpConfig().step(),
                registry.getBaseTimeUnit(), otlpConfig().aggregationTemporality(),
                registry.config().namingConvention());
        otlpMetricConverter.addMeters(registry.getMeters());
        return otlpMetricConverter.getAllMetrics();
    }

    protected void stepOverNStep(int numStepsToSkip) {
        clock.addSeconds(otlpConfig().step().getSeconds() * numStepsToSkip);
    }

    @SuppressWarnings("deprecation")
    protected void assertHistogram(Metric metric, long startTime, long endTime, String unit, long count, double sum,
            double max) {
        assertThat(metric.getHistogram().getAggregationTemporality())
            .isEqualTo(AggregationTemporality.toOtlpAggregationTemporality(otlpConfig().aggregationTemporality()));

        HistogramDataPoint histogram = metric.getHistogram().getDataPoints(0);
        assertThat(metric.getName()).isEqualTo(METER_NAME);
        assertThat(metric.getDescription()).isEqualTo(METER_DESCRIPTION);
        assertThat(metric.getUnit()).isEqualTo(unit);
        assertThat(histogram.getStartTimeUnixNano()).isEqualTo(startTime);
        assertThat(histogram.getTimeUnixNano()).isEqualTo(endTime);
        assertThat(histogram.getCount()).isEqualTo(count);
        assertThat(histogram.getSum()).isEqualTo(sum);

        assertThat(histogram.getAttributesCount()).isEqualTo(1);
        assertThat(histogram.getAttributes(0).getKey()).isEqualTo(meterTag.getKey());
        assertThat(histogram.getAttributes(0).getValue().getStringValue()).isEqualTo(meterTag.getValue());

        if (histogram.getExplicitBoundsCount() > 0) {
            assertThat(histogram.getBucketCountsList().stream().mapToLong(Long::longValue).sum()).isEqualTo(count);
            assertThat(histogram.getExplicitBoundsCount() + 1).isEqualTo(histogram.getBucketCountsCount());
        }

        if (otlpConfig().aggregationTemporality() == AggregationTemporality.DELTA) {
            assertThat(histogram.getMax()).isEqualTo(max);
        }
    }

    @SuppressWarnings("deprecation")
    protected void assertSum(Metric metric, long startTime, long endTime, double expectedValue) {
        NumberDataPoint sumDataPoint = metric.getSum().getDataPoints(0);
        assertThat(metric.getName()).isEqualTo(METER_NAME);
        assertThat(metric.getDescription()).isEqualTo(METER_DESCRIPTION);
        assertThat(sumDataPoint.getStartTimeUnixNano()).isEqualTo(startTime);
        assertThat(sumDataPoint.getTimeUnixNano()).isEqualTo(endTime);
        assertThat(sumDataPoint.getAsDouble()).isEqualTo(expectedValue);
        assertThat(sumDataPoint.getAttributesCount()).isEqualTo(1);
        assertThat(sumDataPoint.getAttributes(0).getKey()).isEqualTo(meterTag.getKey());
        assertThat(sumDataPoint.getAttributes(0).getValue().getStringValue()).isEqualTo(meterTag.getValue());
        assertThat(metric.getSum().getAggregationTemporality())
            .isEqualTo(AggregationTemporality.toOtlpAggregationTemporality(otlpConfig().aggregationTemporality()));
    }

}
