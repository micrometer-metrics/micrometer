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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static io.micrometer.registry.otlp.AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA;
import static org.assertj.core.api.Assertions.assertThat;

class DeltaOtlpMeterRegistryTest {

    private static final String METER_NAME = "test.meter";

    private static final String METER_DESCRIPTION = "Sample meter description";

    private static final Tag meterTag = Tag.of("key", "value");

    MockClock clock;

    OtlpConfig otlpConfig = new OtlpConfig() {
        @Override
        public AggregationTemporality aggregationTemporality() {
            return AGGREGATION_TEMPORALITY_DELTA;
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
        Counter counter = Counter.builder(METER_NAME).description(METER_DESCRIPTION).tags(Tags.of(meterTag))
                .register(registry);
        counter.increment();
        counter.increment();
        assertSum(registry.writeCounter(counter), 0, TimeUnit.MINUTES.toNanos(1), 0);
        this.stepOverNStep(1);
        assertSum(registry.writeCounter(counter), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), 2);

        this.stepOverNStep(1);
        counter.increment();
        assertSum(registry.writeCounter(counter), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3), 1);
    }

    @Test
    void functionCounter() {
        AtomicLong atomicLong = new AtomicLong(10);

        FunctionCounter counter = FunctionCounter.builder(METER_NAME, atomicLong, AtomicLong::get)
                .description(METER_DESCRIPTION).tags(Tags.of(meterTag)).baseUnit("milliseconds").register(registry);

        assertSum(registry.writeFunctionCounter(counter), 0, TimeUnit.MINUTES.toNanos(1), 0);
        this.stepOverNStep(1);
        assertSum(registry.writeFunctionCounter(counter), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2), 10);
        this.stepOverNStep(1);
        assertSum(registry.writeFunctionCounter(counter), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3), 0);
    }

    @Test
    void timer() {
        Timer timer = Timer.builder(METER_NAME).description(METER_DESCRIPTION).tags(Tags.of(meterTag))
                .register(registry);
        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(77, TimeUnit.MILLISECONDS);
        timer.record(111, TimeUnit.MILLISECONDS);

        // This is where TimeWindowMax can be painful and make no sense at all. Need to
        // re-visit this. Probably StepMax is what might fit good for OTLP.
        assertHistogram(registry.writeHistogramSupport(timer), 0, TimeUnit.MINUTES.toNanos(1), "milliseconds", 0, 0,
                111);
        this.stepOverNStep(1);
        assertHistogram(registry.writeHistogramSupport(timer), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2),
                "milliseconds", 3, 198, 111);
        timer.record(4, TimeUnit.MILLISECONDS);
        assertHistogram(registry.writeHistogramSupport(timer), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2),
                "milliseconds", 3, 198, 111);
        this.stepOverNStep(1);
        assertHistogram(registry.writeHistogramSupport(timer), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3),
                "milliseconds", 1, 4, 111);

        this.stepOverNStep(2);
        assertHistogram(registry.writeHistogramSupport(timer), TimeUnit.MINUTES.toNanos(4), TimeUnit.MINUTES.toNanos(5),
                "milliseconds", 0, 0, 0);
        timer.record(1, TimeUnit.MILLISECONDS);
        this.stepOverNStep(1);
        assertHistogram(registry.writeHistogramSupport(timer), TimeUnit.MINUTES.toNanos(5), TimeUnit.MINUTES.toNanos(6),
                "milliseconds", 1, 1, 1);
    }

    @Test
    void functionTimer() {
        FunctionTimer functionTimer = FunctionTimer.builder(METER_NAME, this, o -> 5, o -> 127, TimeUnit.MILLISECONDS)
                .description(METER_DESCRIPTION).tags(Tags.of(meterTag)).register(registry);
        assertHistogram(registry.writeFunctionTimer(functionTimer), 0, TimeUnit.MINUTES.toNanos(1), "milliseconds", 0,
                0, 0);
        this.stepOverNStep(1);
        assertHistogram(registry.writeFunctionTimer(functionTimer), TimeUnit.MINUTES.toNanos(1),
                TimeUnit.MINUTES.toNanos(2), "milliseconds", 5, 127, 0);
        this.stepOverNStep(1);
        assertHistogram(registry.writeFunctionTimer(functionTimer), TimeUnit.MINUTES.toNanos(2),
                TimeUnit.MINUTES.toNanos(3), "milliseconds", 0, 0, 0);
    }

    @Test
    void distributionSummary() {
        DistributionSummary size = DistributionSummary.builder(METER_NAME).baseUnit("bytes")
                .description(METER_DESCRIPTION).tags(Tags.of(meterTag)).register(registry);
        size.record(100);
        size.record(15);
        size.record(2233);

        // This is where TimeWindowMax can be painful and make no sense at all. Need to
        // re-visit this. Probably StepMax is what might fit good for OTLP.
        assertHistogram(registry.writeHistogramSupport(size), 0, TimeUnit.MINUTES.toNanos(1), "bytes", 0, 0, 2233);
        this.stepOverNStep(1);
        assertHistogram(registry.writeHistogramSupport(size), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2),
                "bytes", 3, 2348, 2233);
        size.record(204);
        assertHistogram(registry.writeHistogramSupport(size), TimeUnit.MINUTES.toNanos(1), TimeUnit.MINUTES.toNanos(2),
                "bytes", 3, 2348, 2233);
        this.stepOverNStep(1);
        assertHistogram(registry.writeHistogramSupport(size), TimeUnit.MINUTES.toNanos(2), TimeUnit.MINUTES.toNanos(3),
                "bytes", 1, 204, 2233);
    }

    @Test
    void longTaskTimer() {
        LongTaskTimer taskTimer = LongTaskTimer.builder(METER_NAME).description(METER_DESCRIPTION)
                .tags(Tags.of(meterTag)).register(registry);
        LongTaskTimer.Sample task1 = taskTimer.start();
        LongTaskTimer.Sample task2 = taskTimer.start();
        this.stepOverNStep(3);
        assertHistogram(registry.writeHistogramSupport(taskTimer), TimeUnit.MINUTES.toNanos(3),
                TimeUnit.MINUTES.toNanos(4), "milliseconds", 2, 360000, 180000);

        task1.stop();
        assertHistogram(registry.writeHistogramSupport(taskTimer), TimeUnit.MINUTES.toNanos(3),
                TimeUnit.MINUTES.toNanos(4), "milliseconds", 1, 180000, 180000);
        task2.stop();
        this.stepOverNStep(1);
        assertHistogram(registry.writeHistogramSupport(taskTimer), TimeUnit.MINUTES.toNanos(4),
                TimeUnit.MINUTES.toNanos(5), "milliseconds", 0, 0, 0);
    }

    private void assertHistogram(Metric metric, long startTime, long endTime, String unit, long count, double sum,
            double max) {
        HistogramDataPoint histogram = metric.getHistogram().getDataPoints(0);
        assertThat(metric.getName()).hasToString(METER_NAME);
        assertThat(metric.getDescription()).hasToString(METER_DESCRIPTION);
        assertThat(histogram.getStartTimeUnixNano()).isEqualTo(startTime);
        assertThat(histogram.getTimeUnixNano()).isEqualTo(endTime);
        assertThat(histogram.getCount()).isEqualTo(count);
        assertThat(histogram.getSum()).isEqualTo(sum);
        assertThat(histogram.getMax()).isEqualTo(max);
        assertThat(histogram.getAttributesCount()).isEqualTo(1);
        assertThat(histogram.getAttributes(0).getKey()).hasToString(meterTag.getKey());
        assertThat(histogram.getAttributes(0).getValue().getStringValue()).hasToString(meterTag.getValue());
        assertThat(metric.getHistogram().getAggregationTemporality())
                .isEqualTo(AggregationTemporality.mapToOtlp(AGGREGATION_TEMPORALITY_DELTA));
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
        assertThat(metric.getSum().getAggregationTemporality())
                .isEqualTo(AggregationTemporality.mapToOtlp(AGGREGATION_TEMPORALITY_DELTA));
    }

    private void stepOverNStep(int numStepsToSkip) {
        clock.addSeconds(OtlpConfig.DEFAULT.step().getSeconds() * numStepsToSkip);
    }

}
