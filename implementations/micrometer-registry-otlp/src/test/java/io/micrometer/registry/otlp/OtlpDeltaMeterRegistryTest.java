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
import io.opentelemetry.proto.metrics.v1.HistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static io.micrometer.registry.otlp.AggregationTemporality.DELTA;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class OtlpDeltaMeterRegistryTest extends OtlpMeterRegistryTest {

    @BeforeEach
    void init() {
        // Always assume that at least one step is completed.
        stepOverNStep(1);
    }

    @Override
    protected OtlpConfig otlpConfig() {
        return new OtlpConfig() {
            @Override
            public AggregationTemporality aggregationTemporality() {
                return DELTA;
            }

            @Override
            public String get(String key) {
                return null;
            }
        };
    };

    @Test
    void gauge() {
        Gauge gauge = Gauge.builder(METER_NAME, new AtomicInteger(5), AtomicInteger::doubleValue).register(registry);
        Metric metric = writeToMetric(gauge);
        assertThat(metric.getGauge()).isNotNull();
        assertThat(metric.getGauge().getDataPoints(0).getAsDouble()).isEqualTo(5);
        assertThat(metric.getGauge().getDataPoints(0).getTimeUnixNano()).isEqualTo(TimeUnit.MINUTES.toNanos(1));
    }

    @Test
    void timeGauge() {
        TimeGauge timeGauge = TimeGauge.builder("gauge.time", this, TimeUnit.MICROSECONDS, o -> 24).register(registry);

        Metric metric = writeToMetric(timeGauge);
        assertThat(metric.getGauge()).isNotNull();
        assertThat(metric.getGauge().getDataPoints(0).getAsDouble()).isEqualTo(0.024);
        assertThat(metric.getGauge().getDataPoints(0).getTimeUnixNano()).isEqualTo(TimeUnit.MINUTES.toNanos(1));
    }

    @Test
    void counter() {
        Counter counter = Counter.builder(METER_NAME)
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .register(registry);
        counter.increment();
        counter.increment();
        assertSum(writeToMetric(counter), 0, TimeUnit.MINUTES.toNanos(1), 0);
        this.stepOverNStep(1);
        assertSum(writeToMetric(counter), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), 2);

        this.stepOverNStep(1);
        counter.increment();
        assertSum(writeToMetric(counter), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3), 1);
    }

    @Test
    void functionCounter() {
        AtomicLong atomicLong = new AtomicLong(10);

        FunctionCounter counter = FunctionCounter.builder(METER_NAME, atomicLong, AtomicLong::get)
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .baseUnit("milliseconds")
            .register(registry);

        assertSum(writeToMetric(counter), 0, TimeUnit.MINUTES.toNanos(1), 0);
        this.stepOverNStep(1);
        assertSum(writeToMetric(counter), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), 10);
        this.stepOverNStep(1);
        assertSum(writeToMetric(counter), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3), 0);
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

        assertHistogram(writeToMetric(timer), 0, TimeUnit.MINUTES.toNanos(1), "milliseconds", 0, 0, 0);
        this.stepOverNStep(1);
        assertHistogram(writeToMetric(timer), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), "milliseconds",
                3, 198, 111);
        timer.record(4, TimeUnit.MILLISECONDS);
        assertHistogram(writeToMetric(timer), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), "milliseconds",
                3, 198, 111);
        this.stepOverNStep(1);
        assertHistogram(writeToMetric(timer), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3), "milliseconds",
                1, 4, 4);

        this.stepOverNStep(2);
        assertHistogram(writeToMetric(timer), TimeUnit.MINUTES.toNanos(4), TimeUnit.MINUTES.toNanos(5), "milliseconds",
                0, 0, 0);
        timer.record(1, TimeUnit.MILLISECONDS);
        this.stepOverNStep(1);
        assertHistogram(writeToMetric(timer), TimeUnit.MINUTES.toNanos(5), TimeUnit.MINUTES.toNanos(6), "milliseconds",
                1, 1, 1);
    }

    @Test
    void timerWithHistogram() {
        Timer timer = Timer.builder(METER_NAME)
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .serviceLevelObjectives(Duration.ofMillis(10), Duration.ofMillis(50), Duration.ofMillis(100),
                    Duration.ofMillis(500))
            .register(registry);

        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(77, TimeUnit.MILLISECONDS);
        timer.record(111, TimeUnit.MILLISECONDS);

        HistogramDataPoint histogramDataPoint = writeToMetric(timer).getHistogram().getDataPoints(0);
        assertThat(histogramDataPoint.getExplicitBoundsCount()).isEqualTo(4);
        this.stepOverNStep(1);
        assertHistogram(writeToMetric(timer), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), "milliseconds",
                3, 198, 111);

        histogramDataPoint = writeToMetric(timer).getHistogram().getDataPoints(0);
        assertThat(histogramDataPoint.getExplicitBoundsCount()).isEqualTo(4);

        assertThat(histogramDataPoint.getExplicitBounds(0)).isEqualTo(10.0);
        assertThat(histogramDataPoint.getBucketCounts(0)).isEqualTo(1);
        assertThat(histogramDataPoint.getExplicitBounds(1)).isEqualTo(50.0);
        assertThat(histogramDataPoint.getBucketCounts(1)).isZero();
        assertThat(histogramDataPoint.getExplicitBounds(2)).isEqualTo(100.0);
        assertThat(histogramDataPoint.getBucketCounts(2)).isEqualTo(1);
        assertThat(histogramDataPoint.getExplicitBounds(3)).isEqualTo(500.0);
        assertThat(histogramDataPoint.getBucketCounts(3)).isEqualTo(1);

        timer.record(4, TimeUnit.MILLISECONDS);
        this.stepOverNStep(1);

        histogramDataPoint = writeToMetric(timer).getHistogram().getDataPoints(0);
        assertHistogram(writeToMetric(timer), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3), "milliseconds",
                1, 4, 4);

        assertThat(histogramDataPoint.getBucketCounts(0)).isEqualTo(1);
        assertThat(histogramDataPoint.getBucketCounts(1)).isZero();
        assertThat(histogramDataPoint.getBucketCounts(2)).isZero();
        assertThat(histogramDataPoint.getBucketCounts(3)).isZero();

        timer.record(4, TimeUnit.MILLISECONDS);
        this.stepOverNStep(2);
        histogramDataPoint = writeToMetric(timer).getHistogram().getDataPoints(0);
        assertThat(histogramDataPoint.getBucketCounts(0)).isZero();
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
        assertHistogram(writeToMetric(functionTimer), 0, TimeUnit.MINUTES.toNanos(1), "milliseconds", 0, 0, 0);
        this.stepOverNStep(1);
        assertHistogram(writeToMetric(functionTimer), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2),
                "milliseconds", 5, 127, 0);
        this.stepOverNStep(1);
        assertHistogram(writeToMetric(functionTimer), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3),
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

        assertHistogram(writeToMetric(size), 0, TimeUnit.MINUTES.toNanos(1), "bytes", 0, 0, 0);
        this.stepOverNStep(1);
        assertHistogram(writeToMetric(size), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), "bytes", 3, 2348,
                2233);
        size.record(204);
        assertHistogram(writeToMetric(size), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), "bytes", 3, 2348,
                2233);
        this.stepOverNStep(1);
        assertHistogram(writeToMetric(size), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3), "bytes", 1, 204,
                204);
    }

    @Test
    void distributionSummaryWithHistogram() {
        DistributionSummary ds = DistributionSummary.builder(METER_NAME)
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .baseUnit("bytes")
            .serviceLevelObjectives(10, 50, 100, 500)
            .register(registry);

        assertHistogram(writeToMetric(ds), 0, TimeUnit.MINUTES.toNanos(1), "bytes", 0, 0, 0);
        ds.record(10);
        ds.record(77);
        ds.record(111);
        assertHistogram(writeToMetric(ds), 0, TimeUnit.MINUTES.toNanos(1), "bytes", 0, 0, 0);

        HistogramDataPoint histogramDataPoint = writeToMetric(ds).getHistogram().getDataPoints(0);
        assertThat(histogramDataPoint.getExplicitBoundsCount()).isEqualTo(4);
        this.stepOverNStep(1);
        assertHistogram(writeToMetric(ds), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), "bytes", 3, 198,
                111);

        histogramDataPoint = writeToMetric(ds).getHistogram().getDataPoints(0);
        assertThat(histogramDataPoint.getExplicitBoundsCount()).isEqualTo(4);

        assertThat(histogramDataPoint.getExplicitBounds(0)).isEqualTo(10);
        assertThat(histogramDataPoint.getBucketCounts(0)).isEqualTo(1);
        assertThat(histogramDataPoint.getExplicitBounds(1)).isEqualTo(50);
        assertThat(histogramDataPoint.getBucketCounts(1)).isZero();
        assertThat(histogramDataPoint.getExplicitBounds(2)).isEqualTo(100);
        assertThat(histogramDataPoint.getBucketCounts(2)).isEqualTo(1);
        assertThat(histogramDataPoint.getExplicitBounds(3)).isEqualTo(500);
        assertThat(histogramDataPoint.getBucketCounts(3)).isEqualTo(1);

        this.stepOverNStep(1);
        ds.record(4);
        clock.addSeconds(otlpConfig().step().getSeconds() - 5);

        histogramDataPoint = writeToMetric(ds).getHistogram().getDataPoints(0);
        assertHistogram(writeToMetric(ds), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3), "bytes", 1, 4, 4);

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
        assertHistogram(writeToMetric(taskTimer), TimeUnit.MINUTES.toNanos(3), TimeUnit.MINUTES.toNanos(4),
                "milliseconds", 2, 360000, 180000);

        task1.stop();
        assertHistogram(writeToMetric(taskTimer), TimeUnit.MINUTES.toNanos(3), TimeUnit.MINUTES.toNanos(4),
                "milliseconds", 1, 180000, 180000);
        task2.stop();
        this.stepOverNStep(1);
        assertHistogram(writeToMetric(taskTimer), TimeUnit.MINUTES.toNanos(4), TimeUnit.MINUTES.toNanos(5),
                "milliseconds", 0, 0, 0);
    }

    @Test
    void testMetricsStartAndEndTime() {
        Counter counter = Counter.builder("test_publish_time").register(registry);

        Function<Meter, NumberDataPoint> getDataPoint = (Meter meter) -> {
            return writeToMetric(meter).getSum().getDataPoints(0);
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

        this.stepOverNStep(1);
        // simulate this being scheduled at the start of the step
        registry.pollMetersToRollover();

        // these recordings belong to the current step and should not be published
        counter.increment(10);
        functionCount.addAndGet(10);
        assertSum(writeToMetric(counter), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), 1);
        assertThat(writeToMetric(functionCounter).getSum().getDataPoints(0).getAsDouble()).isEqualTo(16);
        assertThat(writeToMetric(functionTimer).getHistogram().getDataPoints(0).getSum()).isEqualTo(16);
        assertThat(writeToMetric(functionTimer).getHistogram().getDataPoints(0).getCount()).isEqualTo(16);

        clock.addSeconds(otlpConfig().step().getSeconds() / 2);
        registry.pollMetersToRollover(); // pollMeters should be idempotent within a time
                                         // window
        assertSum(writeToMetric(counter), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), 1);
        assertThat(writeToMetric(functionCounter).getSum().getDataPoints(0).getAsDouble()).isEqualTo(16);
        assertThat(writeToMetric(functionTimer).getHistogram().getDataPoints(0).getSum()).isEqualTo(16);
        assertThat(writeToMetric(functionTimer).getHistogram().getDataPoints(0).getCount()).isEqualTo(16);

        clock.addSeconds(otlpConfig().step().getSeconds() / 2);
        registry.pollMetersToRollover();
        assertSum(writeToMetric(counter), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3), 10);
        assertThat(writeToMetric(functionCounter).getSum().getDataPoints(0).getAsDouble()).isEqualTo(10);
        assertThat(writeToMetric(functionTimer).getHistogram().getDataPoints(0).getSum()).isEqualTo(10);
        assertThat(writeToMetric(functionTimer).getHistogram().getDataPoints(0).getCount()).isEqualTo(10);
    }

    @Test
    void scheduledRolloverTimer() {
        Timer timer = Timer.builder(METER_NAME)
            .tags(Tags.of(meterTag))
            .description(METER_DESCRIPTION)
            .serviceLevelObjectives(Duration.ofMillis(10), Duration.ofMillis(100))
            .register(registry);

        registry.pollMetersToRollover();
        assertHistogram(writeToMetric(timer), 0, TimeUnit.MINUTES.toNanos(1), "milliseconds", 0, 0, 0);
        timer.record(Duration.ofMillis(5));
        timer.record(Duration.ofMillis(15));
        timer.record(Duration.ofMillis(150));

        assertHistogram(writeToMetric(timer), 0, TimeUnit.MINUTES.toNanos(1), "milliseconds", 0, 0, 0);
        assertThat(writeToMetric(timer).getHistogram().getDataPoints(0).getBucketCountsList()).allMatch(e -> e == 0);
        this.stepOverNStep(1);

        registry.pollMetersToRollover(); // This should roll over the entire Meter to next
                                         // step.
        assertHistogram(writeToMetric(timer), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), "milliseconds",
                3, 170, 150);
        assertThat(writeToMetric(timer).getHistogram().getDataPoints(0).getBucketCountsList()).allMatch(e -> e == 1);
        clock.addSeconds(1);

        timer.record(Duration.ofMillis(160)); // This belongs to current step.
        assertThat(writeToMetric(timer).getHistogram().getDataPoints(0).getBucketCountsList()).allMatch(e -> e == 1);
        assertHistogram(writeToMetric(timer), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), "milliseconds",
                3, 170, 150);

    }

    @Test
    void scheduledRolloverDistributionSummary() {
        DistributionSummary ds = DistributionSummary.builder(METER_NAME)
            .tags(Tags.of(meterTag))
            .baseUnit("bytes")
            .description(METER_DESCRIPTION)
            .serviceLevelObjectives(10, 100)
            .register(registry);

        registry.pollMetersToRollover();
        assertHistogram(writeToMetric(ds), 0, TimeUnit.MINUTES.toNanos(1), "bytes", 0, 0, 0);
        ds.record(5);
        ds.record(15);
        ds.record(150);

        assertHistogram(writeToMetric(ds), 0, TimeUnit.MINUTES.toNanos(1), "bytes", 0, 0, 0);
        assertThat(writeToMetric(ds).getHistogram().getDataPoints(0).getBucketCountsList()).allMatch(e -> e == 0);
        this.stepOverNStep(1);

        registry.pollMetersToRollover(); // This should roll over the entire Meter to next
        // step.
        assertHistogram(writeToMetric(ds), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), "bytes", 3, 170,
                150);
        assertThat(writeToMetric(ds).getHistogram().getDataPoints(0).getBucketCountsList()).allMatch(e -> e == 1);
        clock.addSeconds(1);

        ds.record(160); // This belongs to current step.
        assertThat(writeToMetric(ds).getHistogram().getDataPoints(0).getBucketCountsList()).allMatch(e -> e == 1);
        assertHistogram(writeToMetric(ds), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), "bytes", 3, 170,
                150);

    }

}
