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
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.opentelemetry.sdk.metrics.data.*;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static io.micrometer.registry.otlp.AggregationTemporality.DELTA;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class OtlpDeltaMeterRegistryTest extends OtlpMeterRegistryTest {

    private static final String UNIT_MILLISECONDS = "milliseconds";

    @BeforeEach
    void init() {
        // Always assume that at least one step is completed.
        stepOverNStep(1);
    }

    @Override
    protected OtlpConfig otlpConfig() {
        return new OtlpConfig() {

            @Override
            public int exemplarsSize() {
                return 4;
            }

            @Override
            public AggregationTemporality aggregationTemporality() {
                return DELTA;
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
            public AggregationTemporality aggregationTemporality() {
                return DELTA;
            }

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
        Gauge gauge = Gauge.builder(METER_NAME, new AtomicInteger(5), AtomicInteger::doubleValue).register(registry);
        MetricData metric = writeToMetric(gauge);
        assertThat(metric.getDoubleGaugeData().getPoints().iterator().next().getValue()).isEqualTo(5);
        assertThat(metric.getDoubleGaugeData().getPoints().iterator().next().getEpochNanos())
            .describedAs("Gauges should have timestamp of the instant when data is sampled")
            .isEqualTo(otlpConfig().step().plus(Duration.ofMillis(1)).toNanos());
    }

    @Override
    @Test
    void timeGauge() {
        TimeGauge timeGauge = TimeGauge.builder("gauge.time", this, TimeUnit.MICROSECONDS, o -> 24).register(registry);

        MetricData metric = writeToMetric(timeGauge);
        assertThat(metric.getDoubleGaugeData().getPoints().iterator().next().getValue()).isEqualTo(0.024);
        assertThat(metric.getDoubleGaugeData().getPoints().iterator().next().getEpochNanos())
            .describedAs("Gauges should have timestamp of the instant when data is sampled")
            .isEqualTo(otlpConfig().step().plus(Duration.ofMillis(1)).toNanos());
    }

    @Test
    void counter() {
        Counter counter = Counter.builder(METER_NAME)
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .register(registry);

        DoubleExemplarData e1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                counter::increment, 1);
        DoubleExemplarData e2 = recorder.record("4bf92f3577b34da6a3ce929d0e000002", "00f067aa0b000002",
                counter::increment, 1);
        MetricData metric = writeToMetric(counter);
        assertSum(metric, 0, TimeUnit.MINUTES.toNanos(1), 0);
        assertThat(metric.getDoubleSumData().getPoints().iterator().next().getExemplars()).isEmpty();

        stepOverNStep(1);
        metric = writeToMetric(counter);
        assertSum(metric, TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), 2);
        assertThat(metric.getDoubleSumData().getPoints().iterator().next().getExemplars()).hasSizeBetween(1, 2)
            .containsAnyOf(e1, e2);

        stepOverNStep(1);
        DoubleExemplarData e3 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                counter::increment, 1);
        metric = writeToMetric(counter);
        assertSum(writeToMetric(counter), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3), 1);
        assertThat(metric.getDoubleSumData().getPoints().iterator().next().getExemplars()).singleElement()
            .isEqualTo(e3);
    }

    @Test
    void functionCounter() {
        AtomicLong atomicLong = new AtomicLong(10);

        FunctionCounter counter = FunctionCounter.builder(METER_NAME, atomicLong, AtomicLong::get)
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .baseUnit(UNIT_MILLISECONDS)
            .register(registry);

        assertSum(writeToMetric(counter), 0, TimeUnit.MINUTES.toNanos(1), 0);
        stepOverNStep(1);
        assertSum(writeToMetric(counter), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), 10);
        stepOverNStep(1);
        assertSum(writeToMetric(counter), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3), 0);
    }

    @Test
    void timer() {
        Timer timer = Timer.builder(METER_NAME)
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .register(registry);
        timer.record(10, MILLISECONDS);
        timer.record(77, MILLISECONDS);
        timer.record(111, MILLISECONDS);

        assertHistogram(writeToMetric(timer), 0, TimeUnit.MINUTES.toNanos(1), UNIT_MILLISECONDS, 0, 0, 0);
        stepOverNStep(1);
        assertHistogram(writeToMetric(timer), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2),
                UNIT_MILLISECONDS, 3, 198, 111);
        timer.record(4, MILLISECONDS);
        assertHistogram(writeToMetric(timer), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2),
                UNIT_MILLISECONDS, 3, 198, 111);
        stepOverNStep(1);
        assertHistogram(writeToMetric(timer), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3),
                UNIT_MILLISECONDS, 1, 4, 4);

        stepOverNStep(2);
        assertHistogram(writeToMetric(timer), TimeUnit.MINUTES.toNanos(4), TimeUnit.MINUTES.toNanos(5),
                UNIT_MILLISECONDS, 0, 0, 0);
        timer.record(1, MILLISECONDS);
        stepOverNStep(1);
        assertHistogram(writeToMetric(timer), TimeUnit.MINUTES.toNanos(5), TimeUnit.MINUTES.toNanos(6),
                UNIT_MILLISECONDS, 1, 1, 1);
    }

    @Test
    void distributionSummary() {
        DistributionSummary size = DistributionSummary.builder(METER_NAME)
            .description(METER_DESCRIPTION)
            .baseUnit(BaseUnits.BYTES)
            .tags(Tags.of(meterTag))
            .register(registry);
        size.record(100);
        size.record(15);
        size.record(2233);

        assertHistogram(writeToMetric(size), 0, TimeUnit.MINUTES.toNanos(1), BaseUnits.BYTES, 0, 0, 0);
        stepOverNStep(1);
        assertHistogram(writeToMetric(size), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), BaseUnits.BYTES,
                3, 2348, 2233);
        size.record(204);
        assertHistogram(writeToMetric(size), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), BaseUnits.BYTES,
                3, 2348, 2233);
        stepOverNStep(1);
        assertHistogram(writeToMetric(size), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3), BaseUnits.BYTES,
                1, 204, 204);

        stepOverNStep(2);
        assertHistogram(writeToMetric(size), TimeUnit.MINUTES.toNanos(4), TimeUnit.MINUTES.toNanos(5), BaseUnits.BYTES,
                0, 0, 0);
        size.record(12);
        stepOverNStep(1);
        assertHistogram(writeToMetric(size), TimeUnit.MINUTES.toNanos(5), TimeUnit.MINUTES.toNanos(6), BaseUnits.BYTES,
                1, 12, 12);
    }

    @Test
    void distributionSummaryWithPercentiles() {
        DistributionSummary size = DistributionSummary.builder(METER_NAME)
            .description(METER_DESCRIPTION)
            .baseUnit(BaseUnits.BYTES)
            .publishPercentiles(0.5, 0.9, 0.99)
            .register(registry);
        size.record(100);
        size.record(15);
        size.record(2233);
        stepOverNStep(1);
        size.record(204);

        MetricData metric = writeToMetric(size);
        assertThat(metric.getName()).isEqualTo(METER_NAME);
        assertThat(metric.getDescription()).isEqualTo(METER_DESCRIPTION);
        assertThat(metric.getUnit()).isEqualTo(BaseUnits.BYTES);
        List<SummaryPointData> dataPoints = new ArrayList<>(metric.getSummaryData().getPoints());
        assertThat(dataPoints).hasSize(1);
        List<ValueAtQuantile> quantiles = dataPoints.get(0).getValues();
        assertThat(quantiles).hasSize(3);
        assertThat(quantiles.get(0)).satisfies(quantile -> assertThat(quantile.getQuantile()).isEqualTo(0.5))
            .satisfies(quantile -> assertThat(quantile.getValue()).isEqualTo(200));
        assertThat(quantiles.get(1)).satisfies(quantile -> assertThat(quantile.getQuantile()).isEqualTo(0.9))
            .satisfies(quantile -> assertThat(quantile.getValue()).isEqualTo(200));
        assertThat(quantiles.get(2)).satisfies(quantile -> assertThat(quantile.getQuantile()).isEqualTo(0.99))
            .satisfies(quantile -> assertThat(quantile.getValue()).isEqualTo(200));
    }

    @Test
    void longTaskTimer() {
        LongTaskTimer taskTimer = LongTaskTimer.builder(METER_NAME)
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .register(registry);
        LongTaskTimer.Sample task1 = taskTimer.start();
        LongTaskTimer.Sample task2 = taskTimer.start();
        stepOverNStep(3);
        assertHistogram(writeToMetric(taskTimer), TimeUnit.MINUTES.toNanos(3), TimeUnit.MINUTES.toNanos(4),
                UNIT_MILLISECONDS, 2, 360000, 180000);

        task1.stop();
        assertHistogram(writeToMetric(taskTimer), TimeUnit.MINUTES.toNanos(3), TimeUnit.MINUTES.toNanos(4),
                UNIT_MILLISECONDS, 1, 180000, 180000);
        task2.stop();
        stepOverNStep(1);
        assertHistogram(writeToMetric(taskTimer), TimeUnit.MINUTES.toNanos(4), TimeUnit.MINUTES.toNanos(5),
                UNIT_MILLISECONDS, 0, 0, 0);
    }

    @Override
    @Test
    void testMetricsStartAndEndTime() {
        Counter counter = Counter.builder("test_publish_time").register(registry);

        Function<Meter, DoublePointData> getDataPoint = (
                meter) -> writeToMetric(meter).getDoubleSumData().getPoints().iterator().next();
        assertThat(getDataPoint.apply(counter).getStartEpochNanos()).isEqualTo(0);
        assertThat(getDataPoint.apply(counter).getEpochNanos()).isEqualTo(60000000000L);
        clock.addSeconds(otlpConfig().step().toSeconds() - 1);
        assertThat(getDataPoint.apply(counter).getStartEpochNanos()).isEqualTo(0);
        assertThat(getDataPoint.apply(counter).getEpochNanos()).isEqualTo(60000000000L);
        clock.addSeconds(1);
        assertThat(getDataPoint.apply(counter).getStartEpochNanos()).isEqualTo(60000000000L);
        assertThat(getDataPoint.apply(counter).getEpochNanos()).isEqualTo(120000000000L);
    }

    @Test
    void scheduledRollOver() {
        Counter counter = Counter.builder(METER_NAME)
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .register(registry);

        AtomicLong functionCount = new AtomicLong(15);
        FunctionCounter functionCounter = FunctionCounter.builder("counter.function", functionCount, AtomicLong::get)
            .register(registry);
        FunctionTimer functionTimer = FunctionTimer
            .builder("timer.function", functionCount, AtomicLong::get, AtomicLong::get, MILLISECONDS)
            .register(registry);

        counter.increment();
        functionCount.incrementAndGet();
        // before rollover
        assertSum(writeToMetric(counter), 0, TimeUnit.MINUTES.toNanos(1), 0);
        assertThat(functionCounter.count()).isZero();
        assertThat(functionTimer.count()).isZero();
        assertThat(functionTimer.totalTime(MILLISECONDS)).isZero();

        stepOverNStep(1);
        // simulate this being scheduled at the start of the step
        registry.pollMetersToRollover();

        // these recordings belong to the current step and should not be published
        counter.increment(10);
        functionCount.addAndGet(10);
        assertSum(writeToMetric(counter), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), 1);
        assertThat(writeToMetric(functionCounter).getDoubleSumData().getPoints().iterator().next().getValue())
            .isEqualTo(16);
        assertThat(writeToMetric(functionTimer).getHistogramData().getPoints().iterator().next().getSum())
            .isEqualTo(16);
        assertThat(writeToMetric(functionTimer).getHistogramData().getPoints().iterator().next().getCount())
            .isEqualTo(16);

        clock.addSeconds(otlpConfig().step().toSeconds() / 2);
        // pollMeters should be idempotent within a time window
        registry.pollMetersToRollover();
        assertSum(writeToMetric(counter), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), 1);
        assertThat(writeToMetric(functionCounter).getDoubleSumData().getPoints().iterator().next().getValue())
            .isEqualTo(16);
        assertThat(writeToMetric(functionTimer).getHistogramData().getPoints().iterator().next().getSum())
            .isEqualTo(16);
        assertThat(writeToMetric(functionTimer).getHistogramData().getPoints().iterator().next().getCount())
            .isEqualTo(16);

        clock.addSeconds(otlpConfig().step().toSeconds() / 2);
        registry.pollMetersToRollover();
        assertSum(writeToMetric(counter), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3), 10);
        assertThat(writeToMetric(functionCounter).getDoubleSumData().getPoints().iterator().next().getValue())
            .isEqualTo(10);
        assertThat(writeToMetric(functionTimer).getHistogramData().getPoints().iterator().next().getSum())
            .isEqualTo(10);
        assertThat(writeToMetric(functionTimer).getHistogramData().getPoints().iterator().next().getCount())
            .isEqualTo(10);
    }

    @Test
    void scheduledRolloverTimer() {
        Timer timer = Timer.builder(METER_NAME)
            .tags(Tags.of(meterTag))
            .description(METER_DESCRIPTION)
            .serviceLevelObjectives(Duration.ofMillis(10), Duration.ofMillis(100))
            .register(registry);

        registry.pollMetersToRollover();
        assertHistogram(writeToMetric(timer), 0, TimeUnit.MINUTES.toNanos(1), UNIT_MILLISECONDS, 0, 0, 0);
        timer.record(Duration.ofMillis(5));
        timer.record(Duration.ofMillis(15));
        timer.record(Duration.ofMillis(150));

        assertHistogram(writeToMetric(timer), 0, TimeUnit.MINUTES.toNanos(1), UNIT_MILLISECONDS, 0, 0, 0);
        assertThat(writeToMetric(timer).getHistogramData().getPoints().iterator().next().getCounts())
            .allMatch(e -> e == 0);
        stepOverNStep(1);

        // This should roll over the entire Meter to next step.
        registry.pollMetersToRollover();
        assertHistogram(writeToMetric(timer), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2),
                UNIT_MILLISECONDS, 3, 170, 150);
        assertThat(writeToMetric(timer).getHistogramData().getPoints().iterator().next().getCounts())
            .allMatch(e -> e == 1);
        clock.addSeconds(1);

        timer.record(Duration.ofMillis(160)); // This belongs to current step.
        assertThat(writeToMetric(timer).getHistogramData().getPoints().iterator().next().getCounts())
            .allMatch(e -> e == 1);
        assertHistogram(writeToMetric(timer), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2),
                UNIT_MILLISECONDS, 3, 170, 150);

    }

    @Test
    void scheduledRolloverDistributionSummary() {
        DistributionSummary ds = DistributionSummary.builder(METER_NAME)
            .tags(Tags.of(meterTag))
            .baseUnit(BaseUnits.BYTES)
            .description(METER_DESCRIPTION)
            .serviceLevelObjectives(10, 100)
            .register(registry);

        registry.pollMetersToRollover();
        assertHistogram(writeToMetric(ds), 0, TimeUnit.MINUTES.toNanos(1), BaseUnits.BYTES, 0, 0, 0);
        ds.record(5);
        ds.record(15);
        ds.record(150);

        assertHistogram(writeToMetric(ds), 0, TimeUnit.MINUTES.toNanos(1), BaseUnits.BYTES, 0, 0, 0);
        assertThat(writeToMetric(ds).getHistogramData().getPoints().iterator().next().getCounts())
            .allMatch(e -> e == 0);
        stepOverNStep(1);

        registry.pollMetersToRollover(); // This should roll over the entire Meter to next
                                         // step.
        assertHistogram(writeToMetric(ds), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), BaseUnits.BYTES, 3,
                170, 150);
        assertThat(writeToMetric(ds).getHistogramData().getPoints().iterator().next().getCounts())
            .allMatch(e -> e == 1);
        clock.addSeconds(1);

        ds.record(160); // This belongs to current step.
        assertThat(writeToMetric(ds).getHistogramData().getPoints().iterator().next().getCounts())
            .allMatch(e -> e == 1);
        assertHistogram(writeToMetric(ds), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), BaseUnits.BYTES, 3,
                170, 150);
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

        clock.add(exponentialHistogramOtlpConfig().step());
        registryWithExponentialHistogram.publish();
        timer.record(Duration.ofMillis(10000));

        MetricData metric = writeToMetric(timer);
        assertThat(metric.getExponentialHistogramData().getPoints()).isNotEmpty();
        ExponentialHistogramPointData exponentialHistogramDataPoint = metric.getExponentialHistogramData()
            .getPoints()
            .iterator()
            .next();
        assertExponentialHistogram(metric, 2, 1100, 1000.0, 0, 5);
        ExponentialHistogramBuckets buckets = exponentialHistogramDataPoint.getPositiveBuckets();
        assertThat(buckets.getOffset()).isEqualTo(212);
        assertThat(buckets.getBucketCounts()).hasSize(107);
        assertThat(buckets.getBucketCounts().get(0)).isEqualTo(1);
        assertThat(buckets.getBucketCounts().get(106)).isEqualTo(1);
        assertThat(buckets.getBucketCounts()).filteredOn(v -> v == 0).hasSize(105);

        clock.add(exponentialHistogramOtlpConfig().step());
        metric = writeToMetric(timer);
        exponentialHistogramDataPoint = metric.getExponentialHistogramData().getPoints().iterator().next();
        assertThat(exponentialHistogramDataPoint.getEpochNanos() - exponentialHistogramDataPoint.getStartEpochNanos())
            .isEqualTo(otlpConfig().step().toNanos());

        assertExponentialHistogram(metric, 1, 10000, 10000.0, 0, 5);

        buckets = exponentialHistogramDataPoint.getPositiveBuckets();
        assertThat(buckets.getOffset()).isEqualTo(425);
        assertThat(buckets.getBucketCounts()).hasSize(1);
        assertThat(buckets.getBucketCounts().get(0)).isEqualTo(1);
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

        clock.add(exponentialHistogramOtlpConfig().step());
        registryWithExponentialHistogram.publish();
        ds.record(10000);

        MetricData metric = writeToMetric(ds);
        assertThat(metric.getExponentialHistogramData().getPoints()).isNotEmpty();
        ExponentialHistogramPointData exponentialHistogramDataPoint = metric.getExponentialHistogramData()
            .getPoints()
            .iterator()
            .next();
        assertExponentialHistogram(metric, 2, 1100, 1000.0, 0, 5);
        ExponentialHistogramBuckets buckets = exponentialHistogramDataPoint.getPositiveBuckets();
        assertThat(buckets.getOffset()).isEqualTo(212);
        assertThat(buckets.getBucketCounts()).hasSize(107);
        assertThat(buckets.getBucketCounts().get(0)).isEqualTo(1);
        assertThat(buckets.getBucketCounts().get(106)).isEqualTo(1);
        assertThat(buckets.getBucketCounts()).filteredOn(v -> v == 0).hasSize(105);

        clock.add(exponentialHistogramOtlpConfig().step());
        metric = writeToMetric(ds);
        exponentialHistogramDataPoint = metric.getExponentialHistogramData().getPoints().iterator().next();
        assertThat(exponentialHistogramDataPoint.getEpochNanos() - exponentialHistogramDataPoint.getStartEpochNanos())
            .isEqualTo(otlpConfig().step().toNanos());

        assertExponentialHistogram(metric, 1, 10000, 10000.0, 0, 5);

        buckets = exponentialHistogramDataPoint.getPositiveBuckets();
        assertThat(buckets.getOffset()).isEqualTo(425);
        assertThat(buckets.getBucketCounts()).hasSize(1);
        assertThat(buckets.getBucketCounts().get(0)).isEqualTo(1);
    }

}
