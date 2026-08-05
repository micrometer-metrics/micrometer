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
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

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
                AggregationTemporality.CUMULATIVE, NamingConvention.dot, true, Resource.empty());
        otlpMeterRegistry = new OtlpMeterRegistry(OtlpConfig.DEFAULT, mockClock);
    }

    @Test
    void sameDescriptionShouldBeSingleMetric() {
        Gauge.builder("test.meter", () -> 1).tags(FIRST_TAG).description("description").register(otlpMeterRegistry);
        Gauge.builder("test.meter", () -> 1).tags(SECOND_TAG).description("description").register(otlpMeterRegistry);

        otlpMetricConverter.addMeters(otlpMeterRegistry.getMeters());
        List<MetricData> metrics = otlpMetricConverter.getAllMetrics();
        assertThat(metrics).singleElement().satisfies(metric -> {
            assertThat(metric.getDescription()).isEqualTo("description");
            assertThat(metric.getDoubleGaugeData().getPoints()).hasSize(2);
        });
    }

    @Test
    void differentDescriptionShouldBeMultipleMetrics() {
        Gauge.builder("test.meter", () -> 1).tags(FIRST_TAG).description("description1").register(otlpMeterRegistry);
        Gauge.builder("test.meter", () -> 1).tags(SECOND_TAG).description("description2").register(otlpMeterRegistry);

        otlpMetricConverter.addMeters(otlpMeterRegistry.getMeters());
        List<MetricData> metrics = otlpMetricConverter.getAllMetrics();

        assertThat(metrics).hasSize(2).satisfiesExactlyInAnyOrder(metric -> {
            assertThat(metric.getDescription()).isEqualTo("description1");
            assertThat(metric.getDoubleGaugeData().getPoints()).hasSize(1);
            assertThat(metric.getDoubleGaugeData().getPoints().iterator().next().getAttributes().size()).isEqualTo(1);
        }, metric -> {
            assertThat(metric.getDescription()).isEqualTo("description2");
            assertThat(metric.getDoubleGaugeData().getPoints()).hasSize(1);
            assertThat(metric.getDoubleGaugeData().getPoints().iterator().next().getAttributes().size()).isEqualTo(1);
        });
    }

    @Test
    void sameBaseUnitShouldBeSingleMetric() {
        Gauge.builder("test.meter", () -> 1).tags(FIRST_TAG).baseUnit("xyz").register(otlpMeterRegistry);
        Gauge.builder("test.meter", () -> 1).tags(SECOND_TAG).baseUnit("xyz").register(otlpMeterRegistry);

        otlpMetricConverter.addMeters(otlpMeterRegistry.getMeters());
        List<MetricData> metrics = otlpMetricConverter.getAllMetrics();
        assertThat(metrics).singleElement().satisfies(metric -> {
            assertThat(metric.getUnit()).isEqualTo("xyz");
            assertThat(metric.getDoubleGaugeData().getPoints()).hasSize(2);
        });
    }

    @Test
    void differentBaseUnitShouldBeMultipleMetrics() {
        Gauge.builder("test.meter", () -> 1).tags(FIRST_TAG).baseUnit("xyz").register(otlpMeterRegistry);
        Gauge.builder("test.meter", () -> 1).tags(SECOND_TAG).baseUnit("abc").register(otlpMeterRegistry);

        otlpMetricConverter.addMeters(otlpMeterRegistry.getMeters());
        List<MetricData> metrics = otlpMetricConverter.getAllMetrics();

        assertThat(metrics).hasSize(2).satisfiesExactlyInAnyOrder(metric -> {
            assertThat(metric.getUnit()).isEqualTo("xyz");
            assertThat(metric.getDoubleGaugeData().getPoints()).hasSize(1);
            assertThat(metric.getDoubleGaugeData().getPoints().iterator().next().getAttributes().size()).isEqualTo(1);
        }, metric -> {
            assertThat(metric.getUnit()).isEqualTo("abc");
            assertThat(metric.getDoubleGaugeData().getPoints()).hasSize(1);
            assertThat(metric.getDoubleGaugeData().getPoints().iterator().next().getAttributes().size()).isEqualTo(1);
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
        List<MetricData> metrics = otlpMetricConverter.getAllMetrics();
        assertThat(metrics).hasSize(3);

        assertThat(metrics).filteredOn(m -> m.getType() == MetricDataType.SUMMARY)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getSummaryData().getPoints()).singleElement()
                .satisfies(summaryDataPoint -> {
                    assertThat(summaryDataPoint.getAttributes().size()).isEqualTo(1);
                    assertThat(summaryDataPoint.getAttributes().get(AttributeKey.stringKey("type")))
                        .isEqualTo("summary");
                    assertThat(summaryDataPoint.getValues()).hasSize(1);
                    assertThat(summaryDataPoint.getValues().get(0).getQuantile()).isEqualTo(0.5);
                }));

        assertThat(metrics).filteredOn(m -> m.getType() == MetricDataType.HISTOGRAM)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getHistogramData().getPoints()).hasSize(2)
                .satisfiesExactlyInAnyOrder(histogramDataPoint -> {
                    assertThat(histogramDataPoint.getAttributes().size()).isEqualTo(1);
                    assertThat(histogramDataPoint.getAttributes().get(AttributeKey.stringKey("type")))
                        .isEqualTo("vanilla");
                    assertThat(histogramDataPoint.getBoundaries()).isEmpty();
                }, histogramDataPoint -> {
                    assertThat(histogramDataPoint.getAttributes().size()).isEqualTo(1);
                    assertThat(histogramDataPoint.getAttributes().get(AttributeKey.stringKey("type")))
                        .isEqualTo("histogram");
                    assertThat(histogramDataPoint.getBoundaries()).hasSize(1);
                    assertThat(histogramDataPoint.getCounts()).hasSize(2);
                }));

        assertThat(metrics).filteredOn(m -> m.getType() == MetricDataType.DOUBLE_GAUGE)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getDoubleGaugeData().getPoints()).hasSize(3)
                .satisfiesExactlyInAnyOrder(gaugeDataPoint -> {
                    assertThat(gaugeDataPoint.getAttributes().size()).isEqualTo(1);
                    assertThat(gaugeDataPoint.getAttributes().get(AttributeKey.stringKey("type"))).isEqualTo("vanilla");
                }, gaugeDataPoint -> {
                    assertThat(gaugeDataPoint.getAttributes().size()).isEqualTo(1);
                    assertThat(gaugeDataPoint.getAttributes().get(AttributeKey.stringKey("type")))
                        .isEqualTo("histogram");
                }, gaugeDataPoint -> {
                    assertThat(gaugeDataPoint.getAttributes().size()).isEqualTo(1);
                    assertThat(gaugeDataPoint.getAttributes().get(AttributeKey.stringKey("type"))).isEqualTo("summary");
                }));
    }

    @Test
    void applyCustomNamingConvention() {
        Gauge gauge = Gauge.builder("test.meter", () -> 1)
            .tags("test.tag", "1")
            .description("description")
            .register(otlpMeterRegistry);

        OtlpMetricConverter otlpMetricConverter = new OtlpMetricConverter(mockClock, Duration.ofMillis(1),
                TimeUnit.MILLISECONDS, AggregationTemporality.CUMULATIVE, NamingConvention.snakeCase, true,
                Resource.empty());
        otlpMetricConverter.addMeter(gauge);

        assertThat(otlpMetricConverter.getAllMetrics()).singleElement().satisfies(metric -> {
            assertThat(metric.getName()).isEqualTo("test_meter");
            assertThat(metric.getDoubleGaugeData().getPoints()).singleElement()
                .satisfies(dataPoint -> assertThat(dataPoint.getAttributes().get(AttributeKey.stringKey("test_tag")))
                    .isEqualTo("1"));
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
        List<MetricData> metrics = otlpMetricConverter.getAllMetrics();
        assertThat(metrics).hasSize(2);
        assertThat(metrics).filteredOn(m -> m.getType() == MetricDataType.SUMMARY)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getSummaryData().getPoints()).singleElement()
                .satisfies(dataPoint -> assertThat(dataPoint.getValues()).singleElement()
                    .satisfies(valueAtQuantile -> assertThat(valueAtQuantile.getValue()).isEqualTo(5))));
        assertThat(metrics).filteredOn(m -> m.getType() == MetricDataType.DOUBLE_GAUGE)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getDoubleGaugeData().getPoints()).hasSize(1));
    }

    @Test
    void shouldNotPublishMaxGaugeWhenPublishHistogramMaxIsFalse() {
        OtlpMetricConverter converterWithoutMax = new OtlpMetricConverter(mockClock, STEP, TimeUnit.MILLISECONDS,
                AggregationTemporality.CUMULATIVE, NamingConvention.dot, false, Resource.empty());

        Timer timer = Timer.builder("test.timer").publishPercentileHistogram().register(otlpMeterRegistry);
        timer.record(Duration.ofMillis(100));
        mockClock.add(STEP);

        converterWithoutMax.addMeter(timer);
        List<MetricData> metrics = converterWithoutMax.getAllMetrics();

        assertThat(metrics).hasSize(1);
        assertThat(metrics).filteredOn(m -> m.getType() == MetricDataType.HISTOGRAM)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getName()).isEqualTo("test.timer"));
        assertThat(metrics).filteredOn(m -> m.getType() == MetricDataType.DOUBLE_GAUGE).isEmpty();
    }

    @Test
    void shouldNotPublishMaxGaugeForDistributionSummaryWhenPublishHistogramMaxIsFalse() {
        OtlpMetricConverter converterWithoutMax = new OtlpMetricConverter(mockClock, STEP, TimeUnit.MILLISECONDS,
                AggregationTemporality.CUMULATIVE, NamingConvention.dot, false, Resource.empty());

        DistributionSummary summary = DistributionSummary.builder("test.summary")
            .publishPercentileHistogram()
            .register(otlpMeterRegistry);
        summary.record(50);
        mockClock.add(STEP);

        converterWithoutMax.addMeter(summary);
        List<MetricData> metrics = converterWithoutMax.getAllMetrics();

        assertThat(metrics).hasSize(1);
        assertThat(metrics).filteredOn(m -> m.getType() == MetricDataType.HISTOGRAM)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getName()).isEqualTo("test.summary"));
        assertThat(metrics).filteredOn(m -> m.getType() == MetricDataType.DOUBLE_GAUGE).isEmpty();
    }

}
