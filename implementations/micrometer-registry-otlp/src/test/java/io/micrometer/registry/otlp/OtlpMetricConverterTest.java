/*
 * Copyright 2024 VMware, Inc.
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
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.registry.otlp.internal.ExponentialHistogramSnapShot;
import io.opentelemetry.proto.metrics.v1.ExponentialHistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.HistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.Metric;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OtlpMetricConverterTest {

    private static final Duration STEP = Duration.ofMillis(1);

    private static final Tags FIRST_TAG = Tags.of("key", "1");

    private static final Tags SECOND_TAG = Tags.of("key", "2");

    MockClock mockClock;

    OtlpMetricConverter otlpMetricConverter;

    OtlpMeterRegistry otlpMeterRegistry;

    @BeforeEach
    void setUp() {
        mockClock = new MockClock();
        otlpMetricConverter = new OtlpMetricConverter(mockClock, STEP, TimeUnit.MILLISECONDS,
                AggregationTemporality.CUMULATIVE, NamingConvention.dot);
        otlpMeterRegistry = new OtlpMeterRegistry(OtlpConfig.DEFAULT, mockClock);
    }

    @Test
    void sameDescriptionShouldBeSingleMetric() {
        Gauge.builder("test.meter", () -> 1).tags(FIRST_TAG).description("description").register(otlpMeterRegistry);
        Gauge.builder("test.meter", () -> 1).tags(SECOND_TAG).description("description").register(otlpMeterRegistry);

        otlpMetricConverter.addMeters(otlpMeterRegistry.getMeters());
        List<Metric> metrics = otlpMetricConverter.getAllMetrics();
        assertThat(metrics).singleElement().satisfies(metric -> {
            assertThat(metric.getDescription()).isEqualTo("description");
            assertThat(metric.getGauge().getDataPointsCount()).isEqualTo(2);
        });
    }

    @Test
    void differentDescriptionShouldBeMultipleMetrics() {
        Gauge.builder("test.meter", () -> 1).tags(FIRST_TAG).description("description1").register(otlpMeterRegistry);
        Gauge.builder("test.meter", () -> 1).tags(SECOND_TAG).description("description2").register(otlpMeterRegistry);

        otlpMetricConverter.addMeters(otlpMeterRegistry.getMeters());
        List<Metric> metrics = otlpMetricConverter.getAllMetrics();

        assertThat(metrics).hasSize(2).satisfiesExactlyInAnyOrder(metric -> {
            assertThat(metric.getDescription()).isEqualTo("description1");
            assertThat(metric.getGauge().getDataPointsCount()).isEqualTo(1);
            assertThat(metric.getGauge().getDataPoints(0).getAttributesList()).hasSize(1);
        }, metric -> {
            assertThat(metric.getDescription()).isEqualTo("description2");
            assertThat(metric.getGauge().getDataPointsCount()).isEqualTo(1);
            assertThat(metric.getGauge().getDataPoints(0).getAttributesList()).hasSize(1);
        });
    }

    @Test
    void sameBaseUnitShouldBeSingleMetric() {
        Gauge.builder("test.meter", () -> 1).tags(FIRST_TAG).baseUnit("xyz").register(otlpMeterRegistry);
        Gauge.builder("test.meter", () -> 1).tags(SECOND_TAG).baseUnit("xyz").register(otlpMeterRegistry);

        otlpMetricConverter.addMeters(otlpMeterRegistry.getMeters());
        List<Metric> metrics = otlpMetricConverter.getAllMetrics();
        assertThat(metrics).singleElement().satisfies(metric -> {
            assertThat(metric.getUnit()).isEqualTo("xyz");
            assertThat(metric.getGauge().getDataPointsCount()).isEqualTo(2);
        });
    }

    @Test
    void differentBaseUnitShouldBeMultipleMetrics() {
        Gauge.builder("test.meter", () -> 1).tags(FIRST_TAG).baseUnit("xyz").register(otlpMeterRegistry);
        Gauge.builder("test.meter", () -> 1).tags(SECOND_TAG).baseUnit("abc").register(otlpMeterRegistry);

        otlpMetricConverter.addMeters(otlpMeterRegistry.getMeters());
        List<Metric> metrics = otlpMetricConverter.getAllMetrics();

        assertThat(metrics).hasSize(2).satisfiesExactlyInAnyOrder(metric -> {
            assertThat(metric.getUnit()).isEqualTo("xyz");
            assertThat(metric.getGauge().getDataPointsCount()).isEqualTo(1);
            assertThat(metric.getGauge().getDataPoints(0).getAttributesList()).hasSize(1);
        }, metric -> {
            assertThat(metric.getUnit()).isEqualTo("abc");
            assertThat(metric.getGauge().getDataPointsCount()).isEqualTo(1);
            assertThat(metric.getGauge().getDataPoints(0).getAttributesList()).hasSize(1);
        });
    }

    @Test
    void timerWithSummaryAndHistogramShouldBeMultipleMetrics() {
        Timer.builder("test.timer")
            .description("description")
            .tag("type", "summary")
            .publishPercentiles(0.5)
            .register(otlpMeterRegistry);
        Timer.builder("test.timer")
            .description("description")
            .tag("type", "histogram")
            .sla(Duration.ofMillis(10))
            .register(otlpMeterRegistry);
        Timer.builder("test.timer").description("description").tag("type", "vanilla").register(otlpMeterRegistry);

        otlpMetricConverter.addMeters(otlpMeterRegistry.getMeters());
        List<Metric> metrics = otlpMetricConverter.getAllMetrics();
        assertThat(metrics).hasSize(2);

        assertThat(metrics).filteredOn(Metric::hasSummary)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getSummary().getDataPointsList()).singleElement()
                .satisfies(summaryDataPoint -> {
                    assertThat(summaryDataPoint.getAttributesCount()).isEqualTo(1);
                    assertThat(summaryDataPoint.getAttributes(0).getValue().getStringValue()).isEqualTo("summary");
                    assertThat(summaryDataPoint.getQuantileValuesCount()).isEqualTo(1);
                    assertThat(summaryDataPoint.getQuantileValues(0).getQuantile()).isEqualTo(0.5);
                }));

        assertThat(metrics).filteredOn(Metric::hasHistogram)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getHistogram().getDataPointsList()).hasSize(2)
                .satisfiesExactlyInAnyOrder(histogramDataPoint -> {
                    assertThat(histogramDataPoint.getAttributesCount()).isEqualTo(1);
                    assertThat(histogramDataPoint.getAttributes(0).getValue().getStringValue()).isEqualTo("vanilla");
                    assertThat(histogramDataPoint.getBucketCountsCount()).isZero();
                }, histogramDataPoint -> {
                    assertThat(histogramDataPoint.getAttributesCount()).isEqualTo(1);
                    assertThat(histogramDataPoint.getAttributes(0).getValue().getStringValue()).isEqualTo("histogram");
                    assertThat(histogramDataPoint.getExplicitBoundsCount()).isEqualTo(1);
                    assertThat(histogramDataPoint.getBucketCountsCount()).isEqualTo(2);
                }));
    }

    @Test
    void applyCustomNamingConvention() {
        Gauge gauge = Gauge.builder("test.meter", () -> 1)
            .tags("test.tag", "1")
            .description("description")
            .register(otlpMeterRegistry);

        OtlpMetricConverter otlpMetricConverter = new OtlpMetricConverter(mockClock, Duration.ofMillis(1),
                TimeUnit.MILLISECONDS, AggregationTemporality.CUMULATIVE, NamingConvention.snakeCase);
        otlpMetricConverter.addMeter(gauge);

        assertThat(otlpMetricConverter.getAllMetrics()).singleElement().satisfies(metric -> {
            assertThat(metric.getName()).isEqualTo("test_meter");
            assertThat(metric.getGauge().getDataPointsList()).singleElement()
                .satisfies(dataPoint -> assertThat(dataPoint.getAttributesList()).singleElement()
                    .satisfies(attribute -> assertThat(attribute.getKey()).isEqualTo("test_tag")));
        });
    }

    @Test
    void addMeterWithDistributionSummary() {
        DistributionSummary summary = DistributionSummary.builder("test.summary")
            .publishPercentiles(0.5)
            .register(otlpMeterRegistry);

        summary.record(5);
        mockClock.add(STEP);

        otlpMetricConverter.addMeter(summary);
        assertThat(otlpMetricConverter.getAllMetrics()).singleElement()
            .satisfies(metric -> assertThat(metric.getSummary().getDataPointsList()).singleElement()
                .satisfies(dataPoint -> assertThat(dataPoint.getQuantileValuesList()).singleElement()
                    .satisfies(valueAtQuantile -> assertThat(valueAtQuantile.getValue()).isEqualTo(5))));
    }

    static class CustomDistributionSummary extends AbstractDistributionSummary
            implements OtlpHistogramSupport, StartTimeAwareMeter {

        private final HistogramSnapshot snapshot;

        private final @Nullable ExponentialHistogramSnapShot expoSnapshot;

        CustomDistributionSummary(Id id, Clock clock, HistogramSnapshot snapshot,
                @Nullable ExponentialHistogramSnapShot expoSnapshot) {
            super(id, clock, DistributionStatisticConfig.DEFAULT, 1.0, false);
            this.snapshot = snapshot;
            this.expoSnapshot = expoSnapshot;
        }

        @Override
        public HistogramSnapshot takeSnapshot() {
            return snapshot;
        }

        @Override
        public @Nullable ExponentialHistogramSnapShot getExponentialHistogramSnapShot() {
            return expoSnapshot;
        }

        @Override
        public long getStartTimeNanos() {
            return 0;
        }

        @Override
        protected void recordNonNegative(double amount) {
        }

        @Override
        public long count() {
            return snapshot.count();
        }

        @Override
        public double totalAmount() {
            return snapshot.total();
        }

        @Override
        public double max() {
            return snapshot.max();
        }

    }

    @Test
    void histogramCountShouldBeSumOfBucketCounts() {
        Meter.Id id = new Meter.Id("test.histogram", Tags.empty(), null, null, Meter.Type.DISTRIBUTION_SUMMARY);
        HistogramSnapshot snapshot = new HistogramSnapshot(10, 100, 20, new ValueAtPercentile[0],
                new CountAtBucket[] { new CountAtBucket(10.0, 3.0), new CountAtBucket(20.0, 5.0) }, null);

        CustomDistributionSummary summary = new CustomDistributionSummary(id, mockClock, snapshot, null);

        otlpMetricConverter.addMeter(summary);
        List<Metric> metrics = otlpMetricConverter.getAllMetrics();

        assertThat(metrics).singleElement().satisfies(metric -> {
            HistogramDataPoint point = metric.getHistogram().getDataPoints(0);
            // Sum of bucket counts is 3 + 5 = 8, while snapshot.count() is 10.
            assertThat(point.getCount()).isEqualTo(8);
        });
    }

    @Test
    void exponentialHistogramCountShouldBeSumOfBucketCounts() {
        Meter.Id id = new Meter.Id("test.exponential.histogram", Tags.empty(), null, null,
                Meter.Type.DISTRIBUTION_SUMMARY);
        HistogramSnapshot snapshot = new HistogramSnapshot(10, 100, 20, new ValueAtPercentile[0], new CountAtBucket[0],
                null);

        ExponentialHistogramSnapShot expoSnapshot = mock(ExponentialHistogramSnapShot.class);
        when(expoSnapshot.scale()).thenReturn(2);
        when(expoSnapshot.zeroCount()).thenReturn(1L);
        when(expoSnapshot.zeroThreshold()).thenReturn(0.0);

        ExponentialHistogramSnapShot.ExponentialBuckets positiveBuckets = mock(
                ExponentialHistogramSnapShot.ExponentialBuckets.class);
        when(positiveBuckets.isEmpty()).thenReturn(false);
        when(positiveBuckets.bucketCounts()).thenReturn(Arrays.asList(2L, 5L));
        when(positiveBuckets.offset()).thenReturn(0);
        when(expoSnapshot.positive()).thenReturn(positiveBuckets);

        CustomDistributionSummary summary = new CustomDistributionSummary(id, mockClock, snapshot, expoSnapshot);

        otlpMetricConverter.addMeter(summary);
        List<Metric> metrics = otlpMetricConverter.getAllMetrics();

        assertThat(metrics).singleElement().satisfies(metric -> {
            ExponentialHistogramDataPoint point = metric.getExponentialHistogram().getDataPoints(0);
            // Sum of zeroCount (1) + positive bucket counts (2 + 5 = 7) = 8, while
            // snapshot.count() is 10.
            assertThat(point.getCount()).isEqualTo(8);
        });
    }

}
