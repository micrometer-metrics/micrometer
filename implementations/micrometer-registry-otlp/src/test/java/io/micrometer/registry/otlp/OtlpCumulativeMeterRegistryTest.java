/*
 * Copyright 2023 VMware, Inc.
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
import io.micrometer.core.instrument.binder.BaseUnits;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.metrics.data.*;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.management.CompilationMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class OtlpCumulativeMeterRegistryTest extends OtlpMeterRegistryTest {

    @Override
    protected OtlpConfig otlpConfig() {
        return new OtlpConfig() {

            @Override
            public int exemplarsSize() {
                return 4;
            }

            @Override
            public @Nullable String get(String key) {
                return null;
            }
        };
    }

    @Override
    OtlpConfig exponentialHistogramOtlpConfig() {
        return new OtlpConfig() {

            @Override
            public HistogramFlavor histogramFlavor() {
                return HistogramFlavor.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM;
            }

            @Override
            public @Nullable String get(String key) {
                return null;
            }
        };
    }

    @Test
    void gauge() {
        Gauge cpus = Gauge
            .builder("cpus", ManagementFactory.getOperatingSystemMXBean(),
                    OperatingSystemMXBean::getAvailableProcessors)
            .register(registry);
        MetricData metric = writeToMetric(cpus);
        assertThat(metric.getName()).isEqualTo("cpus");
        assertThat(metric.getDoubleGaugeData().getPoints()).singleElement().satisfies(point -> {
            assertThat(point.getEpochNanos()).isEqualTo(1000000L);
            assertThat(point.getValue()).isGreaterThan(0.0);
        });
    }

    @Override
    @Test
    void timeGauge() {
        TimeGauge timeGauge = TimeGauge.builder("gauge.time", this, TimeUnit.MICROSECONDS, o -> 24).register(registry);

        MetricData metric = writeToMetric(timeGauge);
        assertThat(metric.getName()).isEqualTo("gauge.time");
        assertThat(metric.getUnit()).isEqualTo("milliseconds");
        assertThat(metric.getDoubleGaugeData().getPoints()).singleElement().satisfies(point -> {
            assertThat(point.getEpochNanos()).isEqualTo(1000000L);
            assertThat(point.getValue()).isEqualTo(0.024);
        });
    }

    @Test
    void counter() {
        Counter counter = registry.counter("log.event", "level", "info");
        counter.increment();
        counter.increment();
        clock.add(otlpConfig().step());
        counter.increment();

        MetricData metric = writeToMetric(counter);
        assertThat(metric.getName()).isEqualTo("log.event");
        assertThat(metric.getDoubleSumData().getPoints()).singleElement().satisfies(point -> {
            assertThat(point.getStartEpochNanos()).isEqualTo(1000000L);
            assertThat(point.getEpochNanos()).isEqualTo(60001000000L);
            assertThat(point.getValue()).isEqualTo(3.0);
            assertThat(point.getAttributes().get(AttributeKey.stringKey("level"))).isEqualTo("info");
        });
        assertThat(metric.getDoubleSumData().getAggregationTemporality())
            .isEqualTo(io.opentelemetry.sdk.metrics.data.AggregationTemporality.CUMULATIVE);
        assertThat(metric.getDoubleSumData().isMonotonic()).isTrue();
    }

    @Test
    void counterWithExemplars() {
        Counter counter = registry.counter("log.event", "level", "info");
        DoubleExemplarData e1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                counter::increment, 1);
        DoubleExemplarData e2 = recorder.record("4bf92f3577b34da6a3ce929d0e000002", "00f067aa0b000002",
                counter::increment, 1);
        clock.add(otlpConfig().step());

        assertThat(writeToMetric(counter).getDoubleSumData().getPoints()).singleElement().satisfies(numberDataPoint -> {
            assertThat(numberDataPoint.getValue()).isEqualTo(2.0);
            assertThat(numberDataPoint.getExemplars()).hasSizeBetween(1, 2).containsAnyOf(e1, e2);
        });

        DoubleExemplarData e3 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                counter::increment, 1);
        clock.add(otlpConfig().step());
        assertThat(writeToMetric(counter).getDoubleSumData().getPoints()).singleElement().satisfies(numberDataPoint -> {
            assertThat(numberDataPoint.getValue()).isEqualTo(3.0);
            assertThat(numberDataPoint.getExemplars()).singleElement().isEqualTo(e3);
        });
    }

    @Test
    void functionCounter() {
        FunctionCounter counter = FunctionCounter
            .builder("jvm.compilation.time", ManagementFactory.getCompilationMXBean(),
                    CompilationMXBean::getTotalCompilationTime)
            .baseUnit("milliseconds")
            .register(registry);

        MetricData metric = writeToMetric(counter);
        assertThat(metric.getName()).isEqualTo("jvm.compilation.time");
        assertThat(metric.getUnit()).isEqualTo("milliseconds");
        assertThat(metric.getDoubleSumData().getPoints()).singleElement().satisfies(point -> {
            assertThat(point.getStartEpochNanos()).isEqualTo(1000000L);
            assertThat(point.getEpochNanos()).isEqualTo(1000000L);
            assertThat(point.getValue()).isGreaterThanOrEqualTo(0.0);
        });
        assertThat(metric.getDoubleSumData().getAggregationTemporality())
            .isEqualTo(io.opentelemetry.sdk.metrics.data.AggregationTemporality.CUMULATIVE);
        assertThat(metric.getDoubleSumData().isMonotonic()).isTrue();
    }

    @Test
    void timer() {
        Timer timer = Timer.builder("web.requests").description("timing web requests").register(registry);
        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(77, TimeUnit.MILLISECONDS);
        timer.record(111, TimeUnit.MILLISECONDS);
        clock.add(otlpConfig().step());
        timer.record(4, TimeUnit.MILLISECONDS);
        List<MetricData> metrics = writeToMetrics(timer);
        MetricData metric = metrics.stream()
            .filter(m -> m.getType() == MetricDataType.HISTOGRAM)
            .findFirst()
            .orElseThrow();
        assertThat(metric.getName()).isEqualTo("web.requests");
        assertThat(metric.getDescription()).isEqualTo("timing web requests");
        assertThat(metric.getUnit()).isEqualTo("milliseconds");
        assertThat(metric.getHistogramData().getPoints()).singleElement().satisfies(point -> {
            assertThat(point.getStartEpochNanos()).isEqualTo(1000000L);
            assertThat(point.getEpochNanos()).isEqualTo(60001000000L);
            assertThat(point.getCount()).isEqualTo(4);
            assertThat(point.getSum()).isEqualTo(202.0);
        });
        assertThat(metric.getHistogramData().getAggregationTemporality())
            .isEqualTo(io.opentelemetry.sdk.metrics.data.AggregationTemporality.CUMULATIVE);
    }

    @Test
    void timerWithHistogram() {
        Timer timer = Timer.builder("http.client.requests").publishPercentileHistogram().register(registry);
        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(77, TimeUnit.MILLISECONDS);
        timer.record(111, TimeUnit.MILLISECONDS);
        timer.record(1, TimeUnit.MINUTES);
        clock.add(otlpConfig().step());
        timer.record(4, TimeUnit.MILLISECONDS);

        List<MetricData> metrics = writeToMetrics(timer);
        MetricData metric = metrics.stream()
            .filter(m -> m.getType() == MetricDataType.HISTOGRAM)
            .findFirst()
            .orElseThrow();
        assertThat(metric.getName()).isEqualTo("http.client.requests");
        assertThat(metric.getUnit()).isEqualTo("milliseconds");
        assertThat(metric.getHistogramData().getPoints()).singleElement().satisfies(point -> {
            assertThat(point.getStartEpochNanos()).isEqualTo(1000000L);
            assertThat(point.getEpochNanos()).isEqualTo(60001000000L);
            assertThat(point.getCount()).isEqualTo(5);
            assertThat(point.getSum()).isEqualTo(60202.0);
            assertThat(point.getCounts().stream().mapToLong(Long::longValue).sum()).isEqualTo(5);
        });
    }

    @Test
    void timerWithPercentiles() {
        Timer timer = Timer.builder("service.requests").publishPercentiles(0.5, 0.9, 0.99).register(registry);
        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(77, TimeUnit.MILLISECONDS);
        timer.record(111, TimeUnit.MILLISECONDS);

        List<MetricData> metrics = writeToMetrics(timer);
        MetricData metric = metrics.stream()
            .filter(m -> m.getType() == MetricDataType.SUMMARY)
            .findFirst()
            .orElseThrow();
        assertThat(metric.getName()).isEqualTo("service.requests");
        assertThat(metric.getUnit()).isEqualTo("milliseconds");
        assertThat(metric.getSummaryData().getPoints()).singleElement().satisfies(point -> {
            assertThat(point.getStartEpochNanos()).isEqualTo(1000000L);
            assertThat(point.getEpochNanos()).isEqualTo(1000000L);
            assertThat(point.getCount()).isEqualTo(3);
            assertThat(point.getSum()).isEqualTo(198.0);
            assertThat(point.getValues()).hasSize(3);
            assertThat(point.getValues().get(0).getQuantile()).isEqualTo(0.5);
            assertThat(point.getValues().get(0).getValue()).isEqualTo(79.167488);
            assertThat(point.getValues().get(1).getQuantile()).isEqualTo(0.9);
            assertThat(point.getValues().get(1).getValue()).isEqualTo(112.72192);
            assertThat(point.getValues().get(2).getQuantile()).isEqualTo(0.99);
            assertThat(point.getValues().get(2).getValue()).isEqualTo(112.72192);
        });
    }

    @Test
    void functionTimer() {
        FunctionTimer functionTimer = FunctionTimer
            .builder("function.timer", this, o -> 5, o -> 127, TimeUnit.MILLISECONDS)
            .register(registry);

        MetricData metric = writeToMetric(functionTimer);
        assertThat(metric.getName()).isEqualTo("function.timer");
        assertThat(metric.getUnit()).isEqualTo("milliseconds");
        assertThat(metric.getHistogramData().getPoints()).singleElement().satisfies(point -> {
            assertThat(point.getStartEpochNanos()).isEqualTo(1000000L);
            assertThat(point.getEpochNanos()).isEqualTo(1000000L);
            assertThat(point.getCount()).isEqualTo(5);
            assertThat(point.getSum()).isEqualTo(127.0);
        });
        assertThat(metric.getHistogramData().getAggregationTemporality())
            .isEqualTo(io.opentelemetry.sdk.metrics.data.AggregationTemporality.CUMULATIVE);
    }

    @Test
    void distributionSummary() {
        DistributionSummary size = DistributionSummary.builder("http.response.size")
            .baseUnit(BaseUnits.BYTES)
            .register(registry);
        size.record(100);
        size.record(15);
        size.record(2233);
        clock.add(otlpConfig().step());
        size.record(204);
        List<MetricData> metrics = writeToMetrics(size);
        MetricData metric = metrics.stream()
            .filter(m -> m.getType() == MetricDataType.HISTOGRAM)
            .findFirst()
            .orElseThrow();
        assertThat(metric.getName()).isEqualTo("http.response.size");
        assertThat(metric.getUnit()).isEqualTo("bytes");
        assertThat(metric.getHistogramData().getPoints()).singleElement().satisfies(point -> {
            assertThat(point.getStartEpochNanos()).isEqualTo(1000000L);
            assertThat(point.getEpochNanos()).isEqualTo(60001000000L);
            assertThat(point.getCount()).isEqualTo(4);
            assertThat(point.getSum()).isEqualTo(2552.0);
        });
        assertThat(metric.getHistogramData().getAggregationTemporality())
            .isEqualTo(io.opentelemetry.sdk.metrics.data.AggregationTemporality.CUMULATIVE);
    }

    @Test
    void distributionSummaryWithHistogram() {
        DistributionSummary size = DistributionSummary.builder("http.request.size")
            .baseUnit(BaseUnits.BYTES)
            .publishPercentileHistogram()
            .register(registry);
        size.record(100);
        size.record(15);
        size.record(2233);
        clock.add(otlpConfig().step());
        size.record(204);

        MetricData metric = writeToMetrics(size).stream()
            .filter(m -> m.getType() == MetricDataType.HISTOGRAM)
            .findFirst()
            .orElseThrow();
        assertThat(metric.getName()).isEqualTo("http.request.size");
        assertThat(metric.getUnit()).isEqualTo("bytes");
        assertThat(metric.getHistogramData().getPoints()).singleElement().satisfies(point -> {
            assertThat(point.getStartEpochNanos()).isEqualTo(1000000L);
            assertThat(point.getEpochNanos()).isEqualTo(60001000000L);
            assertThat(point.getCount()).isEqualTo(4);
            assertThat(point.getSum()).isEqualTo(2552.0);
            assertThat(point.getCounts().stream().mapToLong(Long::longValue).sum()).isEqualTo(4);
        });
    }

    @Test
    void distributionSummaryWithPercentiles() {
        DistributionSummary size = DistributionSummary.builder("http.response.size")
            .baseUnit(BaseUnits.BYTES)
            .publishPercentiles(0.5, 0.9, 0.99)
            .register(registry);
        size.record(100);
        size.record(15);
        size.record(2233);
        clock.add(otlpConfig().step());
        size.record(204);

        List<MetricData> metrics = writeToMetrics(size);
        MetricData metric = metrics.stream()
            .filter(m -> m.getType() == MetricDataType.SUMMARY)
            .findFirst()
            .orElseThrow();
        assertThat(metric.getName()).isEqualTo("http.response.size");
        assertThat(metric.getUnit()).isEqualTo("bytes");
        assertThat(metric.getSummaryData().getPoints()).singleElement().satisfies(point -> {
            assertThat(point.getStartEpochNanos()).isEqualTo(1000000L);
            assertThat(point.getEpochNanos()).isEqualTo(60001000000L);
            assertThat(point.getCount()).isEqualTo(4);
            assertThat(point.getSum()).isEqualTo(2552.0);
            assertThat(point.getValues()).hasSize(3);
            assertThat(point.getValues().get(0).getQuantile()).isEqualTo(0.5);
            assertThat(point.getValues().get(0).getValue()).isEqualTo(200.0);
            assertThat(point.getValues().get(1).getQuantile()).isEqualTo(0.9);
            assertThat(point.getValues().get(1).getValue()).isEqualTo(200.0);
            assertThat(point.getValues().get(2).getQuantile()).isEqualTo(0.99);
            assertThat(point.getValues().get(2).getValue()).isEqualTo(200.0);
        });
    }

    @Test
    void longTaskTimer() {
        LongTaskTimer taskTimer = LongTaskTimer.builder("checkout.batch").register(registry);
        LongTaskTimer.Sample task1 = taskTimer.start();
        LongTaskTimer.Sample task2 = taskTimer.start();
        this.clock.add(otlpConfig().step().multipliedBy(3));

        List<MetricData> metrics = writeToMetrics(taskTimer);
        MetricData metric = metrics.stream()
            .filter(m -> m.getType() == MetricDataType.HISTOGRAM)
            .findFirst()
            .orElseThrow();
        assertThat(metric.getName()).isEqualTo("checkout.batch");
        assertThat(metric.getUnit()).isEqualTo("milliseconds");
        assertThat(metric.getHistogramData().getPoints()).singleElement().satisfies(point -> {
            assertThat(point.getStartEpochNanos()).isEqualTo(1000000L);
            assertThat(point.getEpochNanos()).isEqualTo(180001000000L);
            assertThat(point.getCount()).isEqualTo(2);
            assertThat(point.getSum()).isEqualTo(360000.0);
        });

        task1.stop();
        task2.stop();
        this.clock.add(otlpConfig().step());

        metrics = writeToMetrics(taskTimer);
        metric = metrics.stream().filter(m -> m.getType() == MetricDataType.HISTOGRAM).findFirst().orElseThrow();
        assertThat(metric.getName()).isEqualTo("checkout.batch");
        assertThat(metric.getUnit()).isEqualTo("milliseconds");
        assertThat(metric.getHistogramData().getPoints()).singleElement().satisfies(point -> {
            assertThat(point.getStartEpochNanos()).isEqualTo(1000000L);
            assertThat(point.getEpochNanos()).isEqualTo(240001000000L);
            assertThat(point.getSum()).isEqualTo(0.0);
        });
    }

    @Override
    void testMetricsStartAndEndTime() {
        Counter counter = Counter.builder("test_publish_time").register(registry);
        final long startTime = ((StartTimeAwareMeter) counter).getStartTimeNanos();
        Function<Meter, DoublePointData> getDataPoint = (
                meter) -> writeToMetric(meter).getDoubleSumData().getPoints().iterator().next();
        assertThat(getDataPoint.apply(counter).getStartEpochNanos()).isEqualTo(startTime);
        assertThat(getDataPoint.apply(counter).getEpochNanos()).isEqualTo(1000000L);
        clock.addSeconds(59);
        assertThat(getDataPoint.apply(counter).getStartEpochNanos()).isEqualTo(startTime);
        assertThat(getDataPoint.apply(counter).getEpochNanos()).isEqualTo(59001000000L);
        clock.addSeconds(1);
        assertThat(getDataPoint.apply(counter).getStartEpochNanos()).isEqualTo(startTime);
        assertThat(getDataPoint.apply(counter).getEpochNanos()).isEqualTo(60001000000L);
    }

    @Test
    void testExponentialHistogramWithTimer() {
        Timer timer = Timer.builder(METER_NAME)
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registryWithExponentialHistogram);
        timer.record(Duration.ofMillis(100));
        timer.record(Duration.ofMillis(1000));

        List<MetricData> metrics = writeToMetrics(timer);
        MetricData metric = metrics.stream()
            .filter(m -> m.getType() == MetricDataType.EXPONENTIAL_HISTOGRAM)
            .findFirst()
            .orElseThrow();
        assertThat(metric.getExponentialHistogramData().getPoints()).isNotEmpty();

        ExponentialHistogramPointData exponentialHistogramDataPoint = metric.getExponentialHistogramData()
            .getPoints()
            .iterator()
            .next();
        assertExponentialHistogram(metric, 2, 1100, 0.0, 0, 5);
        ExponentialHistogramBuckets buckets = exponentialHistogramDataPoint.getPositiveBuckets();
        assertThat(buckets.getOffset()).isEqualTo(212);
        assertThat(buckets.getBucketCounts()).hasSize(107);
        assertThat(buckets.getBucketCounts().get(0)).isEqualTo(1);
        assertThat(buckets.getBucketCounts().get(106)).isEqualTo(1);
        assertThat(buckets.getBucketCounts()).filteredOn(v -> v == 0).hasSize(105);

        long previousEndTime = exponentialHistogramDataPoint.getEpochNanos();

        clock.add(exponentialHistogramOtlpConfig().step());
        timer.record(Duration.ofMillis(10000));

        metrics = writeToMetrics(timer);
        metric = metrics.stream()
            .filter(m -> m.getType() == MetricDataType.EXPONENTIAL_HISTOGRAM)
            .findFirst()
            .orElseThrow();
        exponentialHistogramDataPoint = metric.getExponentialHistogramData().getPoints().iterator().next();
        assertThat(exponentialHistogramDataPoint.getEpochNanos() - previousEndTime)
            .isEqualTo(otlpConfig().step().toNanos());

        assertExponentialHistogram(metric, 3, 11100, 0.0, 0, 4);

        buckets = exponentialHistogramDataPoint.getPositiveBuckets();
        assertThat(buckets.getOffset()).isEqualTo(106);
        assertThat(buckets.getBucketCounts()).hasSize(107);
        assertThat(buckets.getBucketCounts().get(0)).isEqualTo(1);
        assertThat(buckets.getBucketCounts().get(53)).isEqualTo(1);
        assertThat(buckets.getBucketCounts().get(106)).isEqualTo(1);
        assertThat(buckets.getBucketCounts()).filteredOn(v -> v == 0).hasSize(104);
    }

    @Test
    void testExponentialHistogramDs() {
        DistributionSummary ds = DistributionSummary.builder(METER_NAME)
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registryWithExponentialHistogram);
        ds.record(100);
        ds.record(1000);

        List<MetricData> metrics = writeToMetrics(ds);
        MetricData metric = metrics.stream()
            .filter(m -> m.getType() == MetricDataType.EXPONENTIAL_HISTOGRAM)
            .findFirst()
            .orElseThrow();
        assertThat(metric.getExponentialHistogramData().getPoints()).isNotEmpty();

        ExponentialHistogramPointData exponentialHistogramDataPoint = metric.getExponentialHistogramData()
            .getPoints()
            .iterator()
            .next();
        assertExponentialHistogram(metric, 2, 1100, 0.0, 0, 5);
        ExponentialHistogramBuckets buckets = exponentialHistogramDataPoint.getPositiveBuckets();
        assertThat(buckets.getOffset()).isEqualTo(212);
        assertThat(buckets.getBucketCounts()).hasSize(107);
        assertThat(buckets.getBucketCounts().get(0)).isEqualTo(1);
        assertThat(buckets.getBucketCounts().get(106)).isEqualTo(1);
        assertThat(buckets.getBucketCounts()).filteredOn(v -> v == 0).hasSize(105);

        long previousEndTime = exponentialHistogramDataPoint.getEpochNanos();

        clock.add(exponentialHistogramOtlpConfig().step());
        ds.record(10000);

        metrics = writeToMetrics(ds);
        metric = metrics.stream()
            .filter(m -> m.getType() == MetricDataType.EXPONENTIAL_HISTOGRAM)
            .findFirst()
            .orElseThrow();
        exponentialHistogramDataPoint = metric.getExponentialHistogramData().getPoints().iterator().next();
        assertThat(exponentialHistogramDataPoint.getEpochNanos() - previousEndTime)
            .isEqualTo(otlpConfig().step().toNanos());

        assertExponentialHistogram(metric, 3, 11100, 0.0, 0, 4);

        buckets = exponentialHistogramDataPoint.getPositiveBuckets();
        assertThat(buckets.getOffset()).isEqualTo(106);
        assertThat(buckets.getBucketCounts()).hasSize(107);
        assertThat(buckets.getBucketCounts().get(0)).isEqualTo(1);
        assertThat(buckets.getBucketCounts().get(53)).isEqualTo(1);
        assertThat(buckets.getBucketCounts().get(106)).isEqualTo(1);
        assertThat(buckets.getBucketCounts()).filteredOn(v -> v == 0).hasSize(104);
    }

}
