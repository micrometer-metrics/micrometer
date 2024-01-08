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
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.proto.metrics.v1.ExponentialHistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.HistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
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

    OtlpMeterRegistry registry = new OtlpMeterRegistry(otlpConfig(), clock);

    OtlpMeterRegistry registryWithExponentialHistogram = new OtlpMeterRegistry(exponentialHistogramOtlpConfig(), clock);

    abstract OtlpConfig otlpConfig();

    abstract OtlpConfig exponentialHistogramOtlpConfig();

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
        Timer.Builder timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentiles(0.5, 0.9);

        DistributionSummary.Builder ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentiles(0.5, 0.9);

        assertThat(writeToMetric(timer.register(registry)).getDataCase().getNumber())
            .isEqualTo(Metric.DataCase.SUMMARY.getNumber());
        assertThat(writeToMetric(ds.register(registry)).getDataCase().getNumber())
            .isEqualTo(Metric.DataCase.SUMMARY.getNumber());
        assertThat(writeToMetric(timer.register(registryWithExponentialHistogram)).getDataCase().getNumber())
            .isEqualTo(Metric.DataCase.SUMMARY.getNumber());
        assertThat(writeToMetric(ds.register(registryWithExponentialHistogram)).getDataCase().getNumber())
            .isEqualTo(Metric.DataCase.SUMMARY.getNumber());
    }

    @Test
    void distributionWithPercentileHistogramShouldWriteHistogramOrExponentialHistogram() {
        Timer.Builder timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram();

        DistributionSummary.Builder ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram();

        assertThat(writeToMetric(timer.register(registry)).getDataCase().getNumber())
            .isEqualTo(Metric.DataCase.HISTOGRAM.getNumber());
        assertThat(writeToMetric(ds.register(registry)).getDataCase().getNumber())
            .isEqualTo(Metric.DataCase.HISTOGRAM.getNumber());
        assertThat(writeToMetric(timer.register(registryWithExponentialHistogram)).getDataCase().getNumber())
            .isEqualTo(Metric.DataCase.EXPONENTIAL_HISTOGRAM.getNumber());
        assertThat(writeToMetric(ds.register(registryWithExponentialHistogram)).getDataCase().getNumber())
            .isEqualTo(Metric.DataCase.EXPONENTIAL_HISTOGRAM.getNumber());
    }

    @Test
    void distributionWithPercentileAndHistogramShouldWriteHistogramOrExponentialHistogram() {
        Timer.Builder timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentiles(0.5, 0.9)
            .publishPercentileHistogram();

        DistributionSummary.Builder ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentiles(0.5, 0.9)
            .publishPercentileHistogram();

        assertThat(writeToMetric(timer.register(registry)).getDataCase().getNumber())
            .isEqualTo(Metric.DataCase.HISTOGRAM.getNumber());
        assertThat(writeToMetric(ds.register(registry)).getDataCase().getNumber())
            .isEqualTo(Metric.DataCase.HISTOGRAM.getNumber());
        assertThat(writeToMetric(timer.register(registryWithExponentialHistogram)).getDataCase().getNumber())
            .isEqualTo(Metric.DataCase.EXPONENTIAL_HISTOGRAM.getNumber());
        assertThat(writeToMetric(ds.register(registryWithExponentialHistogram)).getDataCase().getNumber())
            .isEqualTo(Metric.DataCase.EXPONENTIAL_HISTOGRAM.getNumber());
    }

    @Test
    void distributionWithSLOShouldWriteHistogramDataPoint() {
        Timer.Builder timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .serviceLevelObjectives(Duration.ofMillis(1));
        DistributionSummary.Builder ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .serviceLevelObjectives(1.0);

        assertThat(writeToMetric(timer.register(registry)).getDataCase().getNumber())
            .isEqualTo(Metric.DataCase.HISTOGRAM.getNumber());
        assertThat(writeToMetric(ds.register(registry)).getDataCase().getNumber())
            .isEqualTo(Metric.DataCase.HISTOGRAM.getNumber());
        assertThat(writeToMetric(timer.register(registryWithExponentialHistogram)).getDataCase().getNumber())
            .isEqualTo(Metric.DataCase.HISTOGRAM.getNumber());
        assertThat(writeToMetric(ds.register(registryWithExponentialHistogram)).getDataCase().getNumber())
            .isEqualTo(Metric.DataCase.HISTOGRAM.getNumber());
    }

    @Test
    void testZeroCountForExponentialHistogram() {
        Timer timerWithZero1ms = Timer.builder("zero_count_1ms")
            .publishPercentileHistogram()
            .register(registryWithExponentialHistogram);
        Timer timerWithZero1ns = Timer.builder("zero_count_1ns")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofNanos(1))
            .register(registryWithExponentialHistogram);

        timerWithZero1ms.record(Duration.ofNanos(1));
        timerWithZero1ms.record(Duration.ofMillis(1));
        timerWithZero1ns.record(Duration.ofNanos(1));
        timerWithZero1ns.record(Duration.ofMillis(1));

        clock.add(exponentialHistogramOtlpConfig().step());

        ExponentialHistogramDataPoint dataPoint = writeToMetric(timerWithZero1ms).getExponentialHistogram()
            .getDataPoints(0);
        assertThat(dataPoint.getZeroCount()).isEqualTo(2);
        assertThat(dataPoint.getCount()).isEqualTo(2);
        assertThat(dataPoint.getPositive().getBucketCountsCount()).isZero();

        dataPoint = writeToMetric(timerWithZero1ns).getExponentialHistogram().getDataPoints(0);
        assertThat(dataPoint.getZeroCount()).isEqualTo(1);
        assertThat(dataPoint.getCount()).isEqualTo(2);
        assertThat(dataPoint.getPositive().getBucketCountsCount()).isEqualTo(1);
        assertThat(dataPoint.getPositive().getBucketCountsList()).isEqualTo(List.of(1L));
    }

    @Test
    void timerShouldRecordInBaseUnitForExponentialHistogram() {
        Timer timer = Timer.builder("timer_with_different_units")
            .minimumExpectedValue(Duration.ofNanos(1))
            .publishPercentileHistogram()
            .register(registryWithExponentialHistogram);

        timer.record(Duration.ofNanos(1000)); // 0.001 Milliseconds
        timer.record(Duration.ofMillis(1));
        timer.record(Duration.ofSeconds(1)); // 1000 Milliseconds

        clock.add(exponentialHistogramOtlpConfig().step());

        Metric metric = writeToMetric(timer);
        ExponentialHistogramDataPoint dataPoint = metric.getExponentialHistogram().getDataPoints(0);

        assertThat(dataPoint.getCount()).isEqualTo(3);
        assertThat(dataPoint.getSum()).isEqualTo(1001.001);

        ExponentialHistogramDataPoint.Buckets buckets = dataPoint.getPositive();
        assertThat(buckets.getOffset()).isEqualTo(-80);
        assertThat(buckets.getBucketCountsCount()).isEqualTo(160);
        assertThat(buckets.getBucketCountsList().get(0)).isEqualTo(1);
        assertThat(buckets.getBucketCountsList().get(79)).isEqualTo(1);
        assertThat(buckets.getBucketCountsList().get(159)).isEqualTo(1);
        assertThat(buckets.getBucketCountsList()).filteredOn(v -> v == 0).hasSize(157);
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
        assertMetricMetadata(metric, Optional.of(unit));
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
        assertMetricMetadata(metric, Optional.empty());
        assertThat(sumDataPoint.getStartTimeUnixNano()).isEqualTo(startTime);
        assertThat(sumDataPoint.getTimeUnixNano()).isEqualTo(endTime);
        assertThat(sumDataPoint.getAsDouble()).isEqualTo(expectedValue);
        assertThat(sumDataPoint.getAttributesCount()).isEqualTo(1);
        assertThat(sumDataPoint.getAttributes(0).getKey()).isEqualTo(meterTag.getKey());
        assertThat(sumDataPoint.getAttributes(0).getValue().getStringValue()).isEqualTo(meterTag.getValue());
        assertThat(metric.getSum().getAggregationTemporality())
            .isEqualTo(AggregationTemporality.toOtlpAggregationTemporality(otlpConfig().aggregationTemporality()));
    }

    protected void assertExponentialHistogram(Metric metric, long count, double sum, double max, long zeroCount,
            long scale) {
        assertThat(metric.getExponentialHistogram().getDataPointsCount()).isPositive();
        ExponentialHistogramDataPoint exponentialHistogramDataPoint = metric.getExponentialHistogram().getDataPoints(0);
        assertThat(exponentialHistogramDataPoint.getCount()).isEqualTo(count);
        assertThat(exponentialHistogramDataPoint.getSum()).isEqualTo(sum);
        assertThat(exponentialHistogramDataPoint.getMax()).isEqualTo(max);

        assertThat(exponentialHistogramDataPoint.getScale()).isEqualTo(scale);
        assertThat(exponentialHistogramDataPoint.getZeroCount()).isEqualTo(zeroCount);
        assertThat(exponentialHistogramDataPoint.getNegative().getBucketCountsCount()).isZero();
    }

    private void assertMetricMetadata(final Metric metric, Optional<String> unitOptional) {
        assertThat(metric.getName()).isEqualTo(METER_NAME);
        assertThat(metric.getDescription()).isEqualTo(METER_DESCRIPTION);
        unitOptional.ifPresent(unit -> assertThat(metric.getUnit()).isEqualTo(unit));
    }

}
