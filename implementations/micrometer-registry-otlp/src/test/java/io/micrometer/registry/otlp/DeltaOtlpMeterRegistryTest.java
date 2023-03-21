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
import io.opentelemetry.proto.metrics.v1.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static io.micrometer.registry.otlp.AggregationTemporality.DELTA;
import static org.assertj.core.api.Assertions.assertThat;

class DeltaOtlpMeterRegistryTest {

    private static final String METER_NAME = "test.meter";

    private static final String METER_DESCRIPTION = "Sample meter description";

    private static final Tag meterTag = Tag.of("key", "value");

    MockClock clock;

    OtlpConfig otlpConfig = new OtlpConfig() {
        @Override
        public AggregationTemporality aggregationTemporality() {
            return DELTA;
        }

        @Override
        public String get(String key) {
            return null;
        }
    };

    OtlpMeterRegistry registry;

    @BeforeEach
    void init() {
        clock = new MockClock();
        registry = new OtlpMeterRegistry(otlpConfig, clock);
        // Always assume that atleast one step is completed.
        this.stepOverNStep(1);
    }

    @Test
    void counter() {
        Counter counter = Counter.builder(METER_NAME)
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .register(registry);
        counter.increment();
        counter.increment();
        assertSum(publishTimeAwareWrite(counter), 0, TimeUnit.MINUTES.toNanos(1), 0);
        this.stepOverNStep(1);
        assertSum(publishTimeAwareWrite(counter), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), 2);

        this.stepOverNStep(1);
        counter.increment();
        assertSum(publishTimeAwareWrite(counter), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3), 1);
    }

    @Test
    void functionCounter() {
        AtomicLong atomicLong = new AtomicLong(10);

        FunctionCounter counter = FunctionCounter.builder(METER_NAME, atomicLong, AtomicLong::get)
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .baseUnit("milliseconds")
            .register(registry);

        assertSum(publishTimeAwareWrite(counter), 0, TimeUnit.MINUTES.toNanos(1), 0);
        this.stepOverNStep(1);
        assertSum(publishTimeAwareWrite(counter), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), 10);
        this.stepOverNStep(1);
        assertSum(publishTimeAwareWrite(counter), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3), 0);
    }

    @Test
    void timer() {
        Timer timer = Timer.builder(METER_NAME)
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .register(registry);
        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(77, TimeUnit.MILLISECONDS);
        timer.record(111, TimeUnit.MILLISECONDS);

        // This is where TimeWindowMax can be painful and make no sense at all. Need to
        // re-visit this. Probably StepMax is what might fit good for OTLP.
        assertHistogram(publishTimeAwareWrite(timer), 0, TimeUnit.MINUTES.toNanos(1), "milliseconds", 0, 0, 111);
        this.stepOverNStep(1);
        assertHistogram(publishTimeAwareWrite(timer), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2),
                "milliseconds", 3, 198, 111);
        timer.record(4, TimeUnit.MILLISECONDS);
        assertHistogram(publishTimeAwareWrite(timer), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2),
                "milliseconds", 3, 198, 111);
        this.stepOverNStep(1);
        assertHistogram(publishTimeAwareWrite(timer), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3),
                "milliseconds", 1, 4, 111);

        this.stepOverNStep(2);
        assertHistogram(publishTimeAwareWrite(timer), TimeUnit.MINUTES.toNanos(4), TimeUnit.MINUTES.toNanos(5),
                "milliseconds", 0, 0, 0);
        timer.record(1, TimeUnit.MILLISECONDS);
        this.stepOverNStep(1);
        assertHistogram(publishTimeAwareWrite(timer), TimeUnit.MINUTES.toNanos(5), TimeUnit.MINUTES.toNanos(6),
                "milliseconds", 1, 1, 1);
    }

    @Test
    void timerWithHistogram() {
        // Crazy hack. I don't think the Default TimeWindowPercentileHistogram was ever
        // accurate for Step based measurements. This just makes sure that we test it in
        // an ideal world.
        clock.add(Duration.ofSeconds(5));

        Timer timer = Timer.builder(METER_NAME)
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .serviceLevelObjectives(Duration.ofMillis(10), Duration.ofMillis(50), Duration.ofMillis(100),
                    Duration.ofMillis(500))
            .register(registry);

        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(77, TimeUnit.MILLISECONDS);
        timer.record(111, TimeUnit.MILLISECONDS);
        clock.addSeconds(otlpConfig.step().getSeconds() - 5);

        assertHistogram(publishTimeAwareWrite(timer), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2),
                "milliseconds", 3, 198, 111);

        HistogramDataPoint histogramDataPoint = publishTimeAwareWrite(timer).getHistogram().getDataPoints(0);
        assertThat(histogramDataPoint.getExplicitBoundsCount()).isEqualTo(4);

        assertThat(histogramDataPoint.getExplicitBounds(0)).isEqualTo(10.0);
        assertThat(histogramDataPoint.getBucketCounts(0)).isEqualTo(1);
        assertThat(histogramDataPoint.getExplicitBounds(1)).isEqualTo(50.0);
        assertThat(histogramDataPoint.getBucketCounts(1)).isZero();
        assertThat(histogramDataPoint.getExplicitBounds(2)).isEqualTo(100.0);
        assertThat(histogramDataPoint.getBucketCounts(2)).isEqualTo(1);
        assertThat(histogramDataPoint.getExplicitBounds(3)).isEqualTo(500.0);
        assertThat(histogramDataPoint.getBucketCounts(3)).isEqualTo(1);

        clock.add(Duration.ofSeconds(5));
        timer.record(4, TimeUnit.MILLISECONDS);
        clock.addSeconds(otlpConfig.step().getSeconds() - 5);

        histogramDataPoint = publishTimeAwareWrite(timer).getHistogram().getDataPoints(0);
        assertHistogram(publishTimeAwareWrite(timer), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3),
                "milliseconds", 1, 4, 111);

        assertThat(histogramDataPoint.getBucketCounts(0)).isEqualTo(1);
        assertThat(histogramDataPoint.getBucketCounts(1)).isZero();
        assertThat(histogramDataPoint.getBucketCounts(2)).isZero();
        assertThat(histogramDataPoint.getBucketCounts(3)).isZero();
    }

    @Test
    void functionTimer() {
        FunctionTimer functionTimer = FunctionTimer.builder(METER_NAME, this, o -> 5, o -> 127, TimeUnit.MILLISECONDS)
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .register(registry);
        assertHistogram(publishTimeAwareWrite(functionTimer), 0, TimeUnit.MINUTES.toNanos(1), "milliseconds", 0, 0, 0);
        this.stepOverNStep(1);
        assertHistogram(publishTimeAwareWrite(functionTimer), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2),
                "milliseconds", 5, 127, 0);
        this.stepOverNStep(1);
        assertHistogram(publishTimeAwareWrite(functionTimer), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3),
                "milliseconds", 0, 0, 0);
    }

    @Test
    void distributionSummary() {
        DistributionSummary size = DistributionSummary.builder(METER_NAME)
            .baseUnit("bytes")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .register(registry);
        size.record(100);
        size.record(15);
        size.record(2233);

        // This is where TimeWindowMax can be painful and make no sense at all. Need to
        // re-visit this. Probably StepMax is what might fit good for OTLP.
        assertHistogram(publishTimeAwareWrite(size), 0, TimeUnit.MINUTES.toNanos(1), "bytes", 0, 0, 2233);
        this.stepOverNStep(1);
        assertHistogram(publishTimeAwareWrite(size), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), "bytes",
                3, 2348, 2233);
        size.record(204);
        assertHistogram(publishTimeAwareWrite(size), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), "bytes",
                3, 2348, 2233);
        this.stepOverNStep(1);
        assertHistogram(publishTimeAwareWrite(size), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3), "bytes",
                1, 204, 2233);
    }

    @Test
    void distributionSummaryWithHistogram() {
        // Crazy hack. I don't think the Default TimeWindowPercentileHistogram was ever
        // accurate for Step based measurements. This just makes sure that we test it in
        // an ideal world.
        clock.add(Duration.ofSeconds(5));

        DistributionSummary ds = DistributionSummary.builder(METER_NAME)
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .baseUnit("bytes")
            .serviceLevelObjectives(10, 50, 100, 500)
            .register(registry);

        ds.record(10);
        ds.record(77);
        ds.record(111);
        clock.addSeconds(otlpConfig.step().getSeconds() - 5);
        assertHistogram(publishTimeAwareWrite(ds), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), "bytes", 3,
                198, 111);

        HistogramDataPoint histogramDataPoint = publishTimeAwareWrite(ds).getHistogram().getDataPoints(0);
        assertThat(histogramDataPoint.getExplicitBoundsCount()).isEqualTo(4);

        assertThat(histogramDataPoint.getExplicitBounds(0)).isEqualTo(10);
        assertThat(histogramDataPoint.getBucketCounts(0)).isEqualTo(1);
        assertThat(histogramDataPoint.getExplicitBounds(1)).isEqualTo(50);
        assertThat(histogramDataPoint.getBucketCounts(1)).isZero();
        assertThat(histogramDataPoint.getExplicitBounds(2)).isEqualTo(100);
        assertThat(histogramDataPoint.getBucketCounts(2)).isEqualTo(1);
        assertThat(histogramDataPoint.getExplicitBounds(3)).isEqualTo(500);
        assertThat(histogramDataPoint.getBucketCounts(3)).isEqualTo(1);

        clock.add(Duration.ofSeconds(5));
        ds.record(4);
        clock.addSeconds(otlpConfig.step().getSeconds() - 5);

        histogramDataPoint = publishTimeAwareWrite(ds).getHistogram().getDataPoints(0);
        assertHistogram(publishTimeAwareWrite(ds), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3), "bytes", 1,
                4, 111);

        assertThat(histogramDataPoint.getBucketCounts(0)).isEqualTo(1);
        assertThat(histogramDataPoint.getBucketCounts(1)).isZero();
        assertThat(histogramDataPoint.getBucketCounts(2)).isZero();
        assertThat(histogramDataPoint.getBucketCounts(3)).isZero();
    }

    @Test
    void longTaskTimer() {
        LongTaskTimer taskTimer = LongTaskTimer.builder(METER_NAME)
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .register(registry);
        LongTaskTimer.Sample task1 = taskTimer.start();
        LongTaskTimer.Sample task2 = taskTimer.start();
        this.stepOverNStep(3);
        assertHistogram(publishTimeAwareWrite(taskTimer), TimeUnit.MINUTES.toNanos(3), TimeUnit.MINUTES.toNanos(4),
                "milliseconds", 2, 360000, 180000);

        task1.stop();
        assertHistogram(publishTimeAwareWrite(taskTimer), TimeUnit.MINUTES.toNanos(3), TimeUnit.MINUTES.toNanos(4),
                "milliseconds", 1, 180000, 180000);
        task2.stop();
        this.stepOverNStep(1);
        assertHistogram(publishTimeAwareWrite(taskTimer), TimeUnit.MINUTES.toNanos(4), TimeUnit.MINUTES.toNanos(5),
                "milliseconds", 0, 0, 0);
    }

    @Test
    void testMetricsStartAndEndTime() {
        Counter counter = Counter.builder("test_publish_time").register(registry);

        Function<Meter, NumberDataPoint> getDataPoint = (Meter meter) -> {
            return publishTimeAwareWrite(meter).getSum().getDataPoints(0);
        };
        assertThat(getDataPoint.apply(counter).getStartTimeUnixNano()).isEqualTo(0);
        assertThat(getDataPoint.apply(counter).getTimeUnixNano()).isEqualTo(60000000000L);
        clock.addSeconds(59);
        assertThat(getDataPoint.apply(counter).getStartTimeUnixNano()).isEqualTo(0);
        assertThat(getDataPoint.apply(counter).getTimeUnixNano()).isEqualTo(60000000000L);
        clock.addSeconds(1);
        assertThat(getDataPoint.apply(counter).getStartTimeUnixNano()).isEqualTo(60000000000L);
        assertThat(getDataPoint.apply(counter).getTimeUnixNano()).isEqualTo(120000000000L);
    }

    private void assertHistogram(Metric metric, long startTime, long endTime, String unit, long count, double sum,
            double max) {
        HistogramDataPoint histogram = metric.getHistogram().getDataPoints(0);
        assertThat(metric.getName()).hasToString(METER_NAME);
        assertThat(metric.getDescription()).hasToString(METER_DESCRIPTION);
        assertThat(metric.getUnit()).hasToString(unit);
        assertThat(histogram.getStartTimeUnixNano()).isEqualTo(startTime);
        assertThat(histogram.getTimeUnixNano()).isEqualTo(endTime);
        assertThat(histogram.getCount()).isEqualTo(count);
        assertThat(histogram.getSum()).isEqualTo(sum);
        assertThat(histogram.getMax()).isEqualTo(max);
        assertThat(histogram.getAttributesCount()).isEqualTo(1);
        assertThat(histogram.getAttributes(0).getKey()).hasToString(meterTag.getKey());
        assertThat(histogram.getAttributes(0).getValue().getStringValue()).hasToString(meterTag.getValue());
        assertThat(metric.getHistogram().getAggregationTemporality())
            .isEqualTo(AggregationTemporality.mapToOtlp(DELTA));
    }

    private void assertSum(Metric metric, long startTime, long endTime, double expectedValue) {
        NumberDataPoint sumDataPoint = metric.getSum().getDataPoints(0);
        assertThat(metric.getName()).hasToString(METER_NAME);
        assertThat(metric.getDescription()).hasToString(METER_DESCRIPTION);
        assertThat(sumDataPoint.getStartTimeUnixNano()).isEqualTo(startTime);
        assertThat(sumDataPoint.getTimeUnixNano()).isEqualTo(endTime);
        assertThat(sumDataPoint.getAsDouble()).isEqualTo(expectedValue);
        assertThat(sumDataPoint.getAttributesCount()).isEqualTo(1);
        assertThat(sumDataPoint.getAttributes(0).getKey()).hasToString(meterTag.getKey());
        assertThat(sumDataPoint.getAttributes(0).getValue().getStringValue()).hasToString(meterTag.getValue());
        assertThat(metric.getSum().getAggregationTemporality()).isEqualTo(AggregationTemporality.mapToOtlp(DELTA));
    }

    private void stepOverNStep(int numStepsToSkip) {
        clock.addSeconds(OtlpConfig.DEFAULT.step().getSeconds() * numStepsToSkip);
    }

    private Metric publishTimeAwareWrite(Meter meter) {
        registry.setPublishTimeNano();
        return meter.match(registry::writeGauge, registry::writeCounter, registry::writeHistogramSupport,
                registry::writeHistogramSupport, registry::writeHistogramSupport, registry::writeGauge,
                registry::writeFunctionCounter, registry::writeFunctionTimer, registry::writeMeter);
    }

}
