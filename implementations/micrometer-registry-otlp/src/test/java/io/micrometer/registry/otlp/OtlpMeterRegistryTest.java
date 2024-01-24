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

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.opentelemetry.proto.metrics.v1.HistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
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

    protected MockClock clock = new MockClock();

    protected OtlpMeterRegistry registry = new OtlpMeterRegistry(otlpConfig(), clock);

    abstract OtlpConfig otlpConfig();

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
        registry.setDeltaAggregationTimeUnixNano();
        return meter.match(registry::writeGauge, registry::writeCounter, registry::writeHistogramSupport,
                registry::writeHistogramSupport, registry::writeHistogramSupport, registry::writeGauge,
                registry::writeFunctionCounter, registry::writeFunctionTimer, registry::writeMeter);
    }

    protected void stepOverNStep(int numStepsToSkip) {
        clock.addSeconds(otlpConfig().step().getSeconds() * numStepsToSkip);
    }

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
