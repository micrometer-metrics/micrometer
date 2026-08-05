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
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.data.*;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static io.micrometer.registry.otlp.HistogramFlavor.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM;
import static io.micrometer.registry.otlp.HistogramFlavor.EXPLICIT_BUCKET_HISTOGRAM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

/**
 * Tests for {@link OtlpMeterRegistry}.
 *
 * @author Tommy Ludwig
 * @author Johnny Lim
 * @author Jonatan Ivanov
 */
abstract class OtlpMeterRegistryTest {

    protected static final String METER_NAME = "test.meter";

    protected static final String METER_DESCRIPTION = "Sample meter description";

    protected static final Tag meterTag = Tag.of("key", "value");

    protected MockClock clock;

    protected ExemplarTestRecorder.TestExemplarContextProvider contextProvider;

    protected ExemplarTestRecorder recorder;

    protected MetricExporter mockMetricExporter;

    OtlpMeterRegistry registry;

    OtlpMeterRegistry registryWithExponentialHistogram;

    abstract OtlpConfig otlpConfig();

    abstract OtlpConfig exponentialHistogramOtlpConfig();

    @BeforeEach
    void setUp() {
        this.clock = new MockClock();
        OtlpConfig config = otlpConfig();
        this.mockMetricExporter = mock(MetricExporter.class);
        when(mockMetricExporter.export(any())).thenReturn(CompletableResultCode.ofSuccess());
        this.contextProvider = new ExemplarTestRecorder.TestExemplarContextProvider();
        this.recorder = new ExemplarTestRecorder(contextProvider, clock);
        this.registry = OtlpMeterRegistry.builder(config)
            .clock(clock)
            .metricExporter(mockMetricExporter)
            .exemplarContextProvider(contextProvider)
            .build();
        this.registryWithExponentialHistogram = OtlpMeterRegistry.builder(exponentialHistogramOtlpConfig())
            .clock(clock)
            .metricExporter(mockMetricExporter)
            .exemplarContextProvider(contextProvider)
            .build();
    }

    // If the service.name was not specified, SDKs MUST fallback to 'unknown_service'
    @Test
    void unknownServiceByDefault() {
        assertThat(registry.getResource().getAttributes().get(AttributeKey.stringKey("service.name")))
            .isEqualTo("unknown_service");
    }

    @Test
    void setServiceNameOverrideMethod() {
        registry = new OtlpMeterRegistry(new OtlpConfig() {
            @Override
            public @Nullable String get(String key) {
                return null;
            }

            @Override
            public Map<String, String> resourceAttributes() {
                return Collections.singletonMap("service.name", "myService");
            }
        }, Clock.SYSTEM);

        assertThat(registry.getResource().getAttributes().get(AttributeKey.stringKey("service.name")))
            .isEqualTo("myService");
    }

    @Test
    void reservedResourceAttributesAreKept() {
        registry = new OtlpMeterRegistry(new OtlpConfig() {
            @Override
            public @Nullable String get(String key) {
                return null;
            }

            @Override
            public Map<String, String> resourceAttributes() {
                return Map.of("telemetry.sdk.language", "no", "telemetry.sdk.version", "no", "telemetry.sdk.name",
                        "no");
            }
        }, Clock.SYSTEM);

        assertThat(registry.getResource().getAttributes().get(AttributeKey.stringKey("telemetry.sdk.language")))
            .isEqualTo("java");
    }

    @Test
    void setResourceAttributesAsString() throws IOException {
        Properties propertiesConfig = new Properties();
        propertiesConfig.load(this.getClass().getResourceAsStream("/otlp-config.properties"));
        registry = new OtlpMeterRegistry(key -> (String) propertiesConfig.get(key), Clock.SYSTEM);
        assertThat(registry.getResource().getAttributes().get(AttributeKey.stringKey("key1"))).isEqualTo("value1");
        assertThat(registry.getResource().getAttributes().get(AttributeKey.stringKey("key2"))).isEqualTo("value2");
    }

    @Test
    void setResourceAttributesFromEnvironmentVariables() throws Exception {
        withEnvironmentVariables("OTEL_RESOURCE_ATTRIBUTES", "a=1,b=2", "OTEL_SERVICE_NAME", "my-service")
            .execute(() -> {
                OtlpMeterRegistry envRegistry = new OtlpMeterRegistry(OtlpConfig.DEFAULT, Clock.SYSTEM);
                assertThat(envRegistry.getResource().getAttributes().get(AttributeKey.stringKey("a"))).isEqualTo("1");
                assertThat(envRegistry.getResource().getAttributes().get(AttributeKey.stringKey("b"))).isEqualTo("2");
                assertThat(envRegistry.getResource().getAttributes().get(AttributeKey.stringKey("service.name")))
                    .isEqualTo("my-service");
            });
    }

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

    @Issue("#5577")
    @Test
    void httpHeaders() throws Throwable {
        writeToMetric(TimeGauge.builder("gauge.time", this, TimeUnit.MICROSECONDS, o -> 24).register(registry));
        registry.publish();

        verify(this.mockMetricExporter).export(any());
    }

    @Test
    void compressionModeFromConfig() throws Exception {
        OtlpConfig configWithCompressionOn = new OtlpConfig() {
            @Override
            public @Nullable String get(String key) {
                return null;
            }

            @Override
            public CompressionMode compressionMode() {
                return CompressionMode.GZIP;
            }
        };

        MetricExporter mockExporter = mock(MetricExporter.class);
        when(mockExporter.export(any())).thenReturn(CompletableResultCode.ofSuccess());
        OtlpMeterRegistry registryWithCompression = OtlpMeterRegistry.builder(configWithCompressionOn)
            .clock(clock)
            .metricExporter(mockExporter)
            .build();

        Counter.builder("test.counter").register(registryWithCompression).increment();
        registryWithCompression.publish();

        verify(mockExporter).export(any());
    }

    @Test
    void counterShouldWriteExemplars() {
        Counter counter = Counter.builder("test.counter").register(registry);
        DoubleExemplarData exemplar = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                () -> counter.increment(3), 3);
        stepOverNStep(1);

        assertThat(writeToMetrics(counter)).singleElement().satisfies(metric -> {
            assertThat(metric.getDoubleSumData().getPoints().iterator().next().getExemplars()).singleElement()
                .isEqualTo(exemplar);
        });
    }

    @Test
    void counterShouldRollOverExemplars() {
        Counter counter = Counter.builder("test.counter").register(registry);
        DoubleExemplarData exemplar = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                () -> counter.increment(3), 3);
        registry.close();

        assertThat(writeToMetrics(counter)).singleElement().satisfies(metric -> {
            assertThat(metric.getDoubleSumData().getPoints().iterator().next().getExemplars()).singleElement()
                .isEqualTo(exemplar);
        });
    }

    @RepeatedTest(10)
    void multipleCounterRecordingsShouldBeRandomlySampled() {
        int exemplarsSize = otlpConfig().exemplarsSize();
        Counter counter = Counter.builder("test.counter").register(registry);
        recorder.recordRandomMeasurements(exemplarsSize, counter::increment);
        stepOverNStep(1);

        assertThat(writeToMetrics(counter)).singleElement().satisfies(metric -> {
            assertThat(metric.getDoubleSumData().getPoints()).hasSize(1);
            assertThat(metric.getDoubleSumData().getPoints().iterator().next().getExemplars()).doesNotHaveDuplicates()
                .hasSizeBetween(1, exemplarsSize)
                .allSatisfy(exemplar -> assertThat(exemplar.getValue()).isBetween(1.0, (double) exemplarsSize));
        });
    }

    @Test
    void distributionWithoutHistogramShouldWriteExemplars() {
        Timer timer = Timer.builder("timer").description(METER_DESCRIPTION).tags(Tags.of(meterTag)).register(registry);
        DoubleExemplarData exemplar1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                () -> timer.record(Duration.ofMillis(42)), 42);

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .register(registry);
        DoubleExemplarData exemplar2 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                () -> ds.record(44), 44);
        stepOverNStep(1);

        assertThat(writeToMetrics(timer)).filteredOn(m -> m.getType() == MetricDataType.HISTOGRAM)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getHistogramData().getPoints()).hasSize(1);
                assertThat(metric.getHistogramData().getPoints().iterator().next().getExemplars()).singleElement()
                    .isEqualTo(exemplar1);
            });

        assertThat(writeToMetrics(ds)).filteredOn(m -> m.getType() == MetricDataType.HISTOGRAM)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getHistogramData().getPoints()).hasSize(1);
                assertThat(metric.getHistogramData().getPoints().iterator().next().getExemplars()).singleElement()
                    .isEqualTo(exemplar2);
            });
    }

    @Test
    void distributionWithoutHistogramShouldRollOverExemplars() {
        Timer timer = Timer.builder("timer").description(METER_DESCRIPTION).tags(Tags.of(meterTag)).register(registry);
        DoubleExemplarData exemplar1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                () -> timer.record(Duration.ofMillis(42)), 42);

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .register(registry);
        DoubleExemplarData exemplar2 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                () -> ds.record(44), 44);

        registry.close();

        assertThat(writeToMetrics(timer)).filteredOn(m -> m.getType() == MetricDataType.HISTOGRAM)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getHistogramData().getPoints()).hasSize(1);
                assertThat(metric.getHistogramData().getPoints().iterator().next().getExemplars()).singleElement()
                    .isEqualTo(exemplar1);
            });

        assertThat(writeToMetrics(ds)).filteredOn(m -> m.getType() == MetricDataType.HISTOGRAM)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getHistogramData().getPoints()).hasSize(1);
                assertThat(metric.getHistogramData().getPoints().iterator().next().getExemplars()).singleElement()
                    .isEqualTo(exemplar2);
            });
    }

    @RepeatedTest(10)
    void multipleTimerRecordingsShouldBeRandomlySampled() {
        int exemplarsSize = otlpConfig().exemplarsSize();
        Timer timer = Timer.builder("timer").description(METER_DESCRIPTION).tags(Tags.of(meterTag)).register(registry);

        recorder.recordRandomMeasurements(exemplarsSize, index -> timer.record(Duration.ofMillis(index)));
        stepOverNStep(1);

        assertThat(writeToMetrics(timer)).filteredOn(m -> m.getType() == MetricDataType.HISTOGRAM)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getHistogramData().getPoints()).hasSize(1);
                assertThat(metric.getHistogramData().getPoints().iterator().next().getExemplars())
                    .doesNotHaveDuplicates()
                    .hasSizeBetween(1, exemplarsSize)
                    .allSatisfy(exemplar -> assertThat(exemplar.getValue()).isBetween(1.0, (double) exemplarsSize));
            });
    }

    @RepeatedTest(10)
    void multipleDistributionSummaryRecordingsShouldBeRandomlySampled() {
        int exemplarsSize = otlpConfig().exemplarsSize();
        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .register(registry);

        recorder.recordRandomMeasurements(exemplarsSize, ds::record);
        stepOverNStep(1);

        assertThat(writeToMetrics(ds)).filteredOn(m -> m.getType() == MetricDataType.HISTOGRAM)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getHistogramData().getPoints()).hasSize(1);
                assertThat(metric.getHistogramData().getPoints().iterator().next().getExemplars())
                    .doesNotHaveDuplicates()
                    .hasSizeBetween(1, exemplarsSize)
                    .allSatisfy(exemplar -> assertThat(exemplar.getValue()).isBetween(1.0, (double) exemplarsSize));
            });
    }

    @Test
    void explicitBucketHistogramShouldWriteExemplars() {
        Timer timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .serviceLevelObjectives(Duration.ofMillis(10), Duration.ofMillis(20))
            .register(registry);

        DoubleExemplarData exemplar1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                () -> timer.record(Duration.ofMillis(5)), 5);
        DoubleExemplarData exemplar2 = recorder.record("4bf92f3577b34da6a3ce929d0e000002", "00f067aa0b000002",
                () -> timer.record(Duration.ofMillis(15)), 15);

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .serviceLevelObjectives(10.0, 20.0)
            .register(registry);

        DoubleExemplarData exemplar3 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                () -> ds.record(5), 5);
        DoubleExemplarData exemplar4 = recorder.record("4bf92f3577b34da6a3ce929d0e000004", "00f067aa0b000004",
                () -> ds.record(15), 15);

        stepOverNStep(1);

        assertThat(writeToMetrics(timer)).filteredOn(m -> m.getType() == MetricDataType.HISTOGRAM)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getHistogramData().getPoints()).hasSize(1);
                assertThat(metric.getHistogramData().getPoints().iterator().next().getExemplars())
                    .containsExactlyInAnyOrder(exemplar1, exemplar2);
            });

        assertThat(writeToMetrics(ds)).filteredOn(m -> m.getType() == MetricDataType.HISTOGRAM)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getHistogramData().getPoints()).hasSize(1);
                assertThat(metric.getHistogramData().getPoints().iterator().next().getExemplars())
                    .containsExactlyInAnyOrder(exemplar3, exemplar4);
            });
    }

    @Test
    void explicitBucketHistogramShouldRollOverExemplars() {
        Timer timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .serviceLevelObjectives(Duration.ofMillis(10), Duration.ofMillis(20))
            .register(registry);

        DoubleExemplarData exemplar1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                () -> timer.record(Duration.ofMillis(5)), 5);
        DoubleExemplarData exemplar2 = recorder.record("4bf92f3577b34da6a3ce929d0e000002", "00f067aa0b000002",
                () -> timer.record(Duration.ofMillis(15)), 15);

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .serviceLevelObjectives(10.0, 20.0)
            .register(registry);

        DoubleExemplarData exemplar3 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                () -> ds.record(5), 5);
        DoubleExemplarData exemplar4 = recorder.record("4bf92f3577b34da6a3ce929d0e000004", "00f067aa0b000004",
                () -> ds.record(15), 15);

        registry.close();

        assertThat(writeToMetrics(timer)).filteredOn(m -> m.getType() == MetricDataType.HISTOGRAM)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getHistogramData().getPoints()).hasSize(1);
                assertThat(metric.getHistogramData().getPoints().iterator().next().getExemplars())
                    .containsExactlyInAnyOrder(exemplar1, exemplar2);
            });

        assertThat(writeToMetrics(ds)).filteredOn(m -> m.getType() == MetricDataType.HISTOGRAM)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getHistogramData().getPoints()).hasSize(1);
                assertThat(metric.getHistogramData().getPoints().iterator().next().getExemplars())
                    .containsExactlyInAnyOrder(exemplar3, exemplar4);
            });
    }

    @Test
    void exponentialHistogramShouldWriteExemplars() {
        Timer timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registryWithExponentialHistogram);

        DoubleExemplarData exemplar1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                () -> timer.record(Duration.ofMillis(42)), 42);

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registryWithExponentialHistogram);

        DoubleExemplarData exemplar2 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                () -> ds.record(44), 44);

        stepOverNStep(1);

        assertThat(writeToMetrics(timer)).filteredOn(m -> m.getType() == MetricDataType.EXPONENTIAL_HISTOGRAM)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getExponentialHistogramData().getPoints()).hasSize(1);
                assertThat(metric.getExponentialHistogramData().getPoints().iterator().next().getExemplars())
                    .singleElement()
                    .isEqualTo(exemplar1);
            });

        assertThat(writeToMetrics(ds)).filteredOn(m -> m.getType() == MetricDataType.EXPONENTIAL_HISTOGRAM)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getExponentialHistogramData().getPoints()).hasSize(1);
                assertThat(metric.getExponentialHistogramData().getPoints().iterator().next().getExemplars())
                    .singleElement()
                    .isEqualTo(exemplar2);
            });
    }

    @Test
    void exponentialHistogramShouldRollOverExemplars() {
        Timer timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registryWithExponentialHistogram);

        DoubleExemplarData exemplar1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                () -> timer.record(Duration.ofMillis(42)), 42);

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registryWithExponentialHistogram);

        DoubleExemplarData exemplar2 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                () -> ds.record(44), 44);

        registryWithExponentialHistogram.close();

        assertThat(writeToMetrics(timer)).filteredOn(m -> m.getType() == MetricDataType.EXPONENTIAL_HISTOGRAM)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getExponentialHistogramData().getPoints()).hasSize(1);
                assertThat(metric.getExponentialHistogramData().getPoints().iterator().next().getExemplars())
                    .singleElement()
                    .isEqualTo(exemplar1);
            });

        assertThat(writeToMetrics(ds)).filteredOn(m -> m.getType() == MetricDataType.EXPONENTIAL_HISTOGRAM)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getExponentialHistogramData().getPoints()).hasSize(1);
                assertThat(metric.getExponentialHistogramData().getPoints().iterator().next().getExemplars())
                    .singleElement()
                    .isEqualTo(exemplar2);
            });
    }

    @RepeatedTest(10)
    void multipleTimerRecordingsShouldBeRandomlySampledWithExponentialHistogram() {
        int size = otlpConfig().maxBucketCount() / 4;
        Timer timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registryWithExponentialHistogram);
        recorder.recordRandomMeasurements(size, index -> timer.record(Duration.ofMillis(index)));

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registryWithExponentialHistogram);
        recorder.recordRandomMeasurements(size, ds::record);

        stepOverNStep(1);

        assertThat(writeToMetrics(timer)).filteredOn(m -> m.getType() == MetricDataType.EXPONENTIAL_HISTOGRAM)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getExponentialHistogramData().getPoints()).hasSize(1);
                assertThat(metric.getExponentialHistogramData().getPoints().iterator().next().getExemplars())
                    .doesNotHaveDuplicates()
                    .hasSizeBetween(1, size)
                    .allSatisfy(exemplar -> assertThat(exemplar.getValue()).isBetween(1.0, (double) size));
            });

        assertThat(writeToMetrics(ds)).filteredOn(m -> m.getType() == MetricDataType.EXPONENTIAL_HISTOGRAM)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getExponentialHistogramData().getPoints()).hasSize(1);
                assertThat(metric.getExponentialHistogramData().getPoints().iterator().next().getExemplars())
                    .doesNotHaveDuplicates()
                    .hasSizeBetween(1, size)
                    .allSatisfy(exemplar -> assertThat(exemplar.getValue()).isBetween(1.0, (double) size));
            });
    }

    @RepeatedTest(10)
    void multipleDistributionSummaryRecordingsShouldBeRandomlySampledWithExponentialHistogram() {
        int size = otlpConfig().maxBucketCount() / 4;
        Timer timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registryWithExponentialHistogram);
        recorder.recordRandomMeasurements(size, index -> timer.record(Duration.ofMillis(index)));

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registryWithExponentialHistogram);
        recorder.recordRandomMeasurements(size, ds::record);

        stepOverNStep(1);

        assertThat(writeToMetrics(timer)).filteredOn(m -> m.getType() == MetricDataType.EXPONENTIAL_HISTOGRAM)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getExponentialHistogramData().getPoints()).hasSize(1);
                assertThat(metric.getExponentialHistogramData().getPoints().iterator().next().getExemplars())
                    .doesNotHaveDuplicates()
                    .hasSizeBetween(1, size)
                    .allSatisfy(exemplar -> assertThat(exemplar.getValue()).isBetween(1.0, (double) size));
            });

        assertThat(writeToMetrics(ds)).filteredOn(m -> m.getType() == MetricDataType.EXPONENTIAL_HISTOGRAM)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getExponentialHistogramData().getPoints()).hasSize(1);
                assertThat(metric.getExponentialHistogramData().getPoints().iterator().next().getExemplars())
                    .doesNotHaveDuplicates()
                    .hasSizeBetween(1, size)
                    .allSatisfy(exemplar -> assertThat(exemplar.getValue()).isBetween(1.0, (double) size));
            });
    }

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

        List<MetricData> metrics = writeAllMeters();
        if (otlpConfig().publishMaxGaugeForHistograms()) {
            assertThat(metrics).hasSize(4);
            assertThat(metrics).filteredOn(m -> m.getType() == MetricDataType.DOUBLE_GAUGE)
                .hasSize(2)
                .satisfiesExactlyInAnyOrder(metric -> {
                    assertThat(metric.getDescription()).isEqualTo("description");
                    assertThat(metric.getDoubleGaugeData().getPoints()).hasSize(2);
                }, metric -> {
                    assertThat(metric.getDescription()).isEqualTo("description");
                    assertThat(metric.getDoubleGaugeData().getPoints()).hasSize(2);
                    assertThat(metric.getUnit()).isEqualTo("milliseconds");
                });
        }
        else {
            assertThat(metrics).hasSize(3);
            assertThat(metrics).filteredOn(m -> m.getType() == MetricDataType.DOUBLE_GAUGE)
                .singleElement()
                .satisfies(metric -> {
                    assertThat(metric.getDescription()).isEqualTo("description");
                    assertThat(metric.getDoubleGaugeData().getPoints()).hasSize(2);
                });
        }

        assertThat(metrics).filteredOn(m -> m.getType() == MetricDataType.DOUBLE_SUM)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getDescription()).isEqualTo("description");
                assertThat(metric.getDoubleSumData().getPoints()).hasSize(2);
                assertThat(metric.getDoubleSumData().getAggregationTemporality()).isEqualTo(
                        AggregationTemporality.toOtlpAggregationTemporality(otlpConfig().aggregationTemporality()));
            });

        assertThat(metrics).filteredOn(m -> m.getType() == MetricDataType.HISTOGRAM)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getDescription()).isEqualTo("description");
                assertThat(metric.getHistogramData().getPoints()).hasSize(2);
                assertThat(metric.getHistogramData().getAggregationTemporality()).isEqualTo(
                        AggregationTemporality.toOtlpAggregationTemporality(otlpConfig().aggregationTemporality()));
            });
    }

    @Test
    void metricsWithDifferentMetadataShouldBeMultipleMetrics() {
        Tags firstTag = Tags.of("key", "first");
        Tags secondTag = Tags.of("key", "second");

        String description1 = "description1";
        String description2 = "description2";
        Gauge.builder("test.gauge", () -> 1).description(description1).tags(firstTag).register(registry);
        Gauge.builder("test.gauge", () -> 1).description(description2).tags(secondTag).register(registry);

        Counter.builder("test.counter").description(description1).tags(firstTag).register(registry);
        Counter.builder("test.counter").baseUnit("xyz").description(description1).tags(secondTag).register(registry);

        Timer.builder("test.timer").description(description1).tags(firstTag).register(registry);
        Timer.builder("test.timer").description(description2).tags(secondTag).register(registry);

        List<MetricData> metrics = writeAllMeters();
        if (otlpConfig().publishMaxGaugeForHistograms()) {
            assertThat(metrics).hasSize(8);
            assertThat(metrics).filteredOn(m -> m.getType() == MetricDataType.DOUBLE_GAUGE)
                .hasSize(4)
                .satisfiesExactlyInAnyOrder(metric -> assertThat(metric.getDescription()).isEqualTo(description1),
                        metric -> assertThat(metric.getDescription()).isEqualTo(description2),
                        metric -> assertThat(metric.getDescription()).isEqualTo(description1),
                        metric -> assertThat(metric.getDescription()).isEqualTo(description2));
        }
        else {
            assertThat(metrics).hasSize(6);
            assertThat(metrics).filteredOn(m -> m.getType() == MetricDataType.DOUBLE_GAUGE)
                .hasSize(2)
                .satisfiesExactlyInAnyOrder(metric -> assertThat(metric.getDescription()).isEqualTo(description1),
                        metric -> assertThat(metric.getDescription()).isEqualTo(description2));

        }

        assertThat(metrics).filteredOn(m -> m.getType() == MetricDataType.DOUBLE_SUM)
            .hasSize(2)
            .satisfiesExactlyInAnyOrder(metric -> assertThat(metric.getUnit()).isEmpty(),
                    metric -> assertThat(metric.getUnit()).isEqualTo("xyz"));

        assertThat(metrics).filteredOn(m -> m.getType() == MetricDataType.HISTOGRAM)
            .hasSize(2)
            .satisfiesExactlyInAnyOrder(metric -> assertThat(metric.getDescription()).isEqualTo(description1),
                    metric -> assertThat(metric.getDescription()).isEqualTo(description2));
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

        List<MetricData> timerMetrics = writeToMetrics(timer.register(registry));
        assertThat(timerMetrics).filteredOn(m -> m.getType() == MetricDataType.HISTOGRAM)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getType()).isEqualTo(MetricDataType.HISTOGRAM));
        assertMaxGaugeMetrics(timerMetrics);
        List<MetricData> dsMetrics = writeToMetrics(ds.register(registry));
        assertThat(dsMetrics).filteredOn(m -> m.getType() == MetricDataType.HISTOGRAM)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getType()).isEqualTo(MetricDataType.HISTOGRAM));
        assertMaxGaugeMetrics(dsMetrics);
        List<MetricData> timerExpoMetrics = writeToMetrics(timer.register(registryWithExponentialHistogram));
        assertThat(timerExpoMetrics).filteredOn(m -> m.getType() == MetricDataType.EXPONENTIAL_HISTOGRAM)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getType()).isEqualTo(MetricDataType.EXPONENTIAL_HISTOGRAM));
        assertMaxGaugeMetrics(timerExpoMetrics);
        List<MetricData> dsExpoMetrics = writeToMetrics(ds.register(registryWithExponentialHistogram));
        assertThat(dsExpoMetrics).filteredOn(m -> m.getType() == MetricDataType.EXPONENTIAL_HISTOGRAM)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getType()).isEqualTo(MetricDataType.EXPONENTIAL_HISTOGRAM));
        assertMaxGaugeMetrics(dsExpoMetrics);

    }

    @Test
    void distributionWithPercentileAndHistogramShouldWriteExemplars() {
        Timer timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentiles(0.5, 0.9)
            .publishPercentileHistogram()
            .register(registry);

        DoubleExemplarData exemplar1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                () -> timer.record(Duration.ofMillis(42)), 42);

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentiles(0.5, 0.9)
            .publishPercentileHistogram()
            .register(registry);

        DoubleExemplarData exemplar2 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                () -> ds.record(44), 44);

        stepOverNStep(1);

        assertThat(writeToMetrics(timer)).filteredOn(m -> m.getType() == MetricDataType.HISTOGRAM)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getHistogramData().getPoints()).hasSize(1);
                assertThat(metric.getHistogramData().getPoints().iterator().next().getExemplars()).singleElement()
                    .isEqualTo(exemplar1);
            });

        assertThat(writeToMetrics(ds)).filteredOn(m -> m.getType() == MetricDataType.HISTOGRAM)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getHistogramData().getPoints()).hasSize(1);
                assertThat(metric.getHistogramData().getPoints().iterator().next().getExemplars()).singleElement()
                    .isEqualTo(exemplar2);
            });
    }

    @Test
    void histogramFlavorPerMeterLookup() {
        Map<String, HistogramFlavor> histogramFlavorPerMeter = new HashMap<>();
        histogramFlavorPerMeter.put("http", EXPLICIT_BUCKET_HISTOGRAM);
        histogramFlavorPerMeter.put("http.server", BASE2_EXPONENTIAL_BUCKET_HISTOGRAM);

        assertThat(OtlpMeterRegistry.HistogramFlavorPerMeterLookup.DEFAULT.getHistogramFlavor(histogramFlavorPerMeter,
                createIdWithName("http.server.requests")))
            .isEqualTo(BASE2_EXPONENTIAL_BUCKET_HISTOGRAM);
        assertThat(OtlpMeterRegistry.HistogramFlavorPerMeterLookup.DEFAULT.getHistogramFlavor(histogramFlavorPerMeter,
                createIdWithName("http.client.requests")))
            .isEqualTo(EXPLICIT_BUCKET_HISTOGRAM);
        assertThat(OtlpMeterRegistry.HistogramFlavorPerMeterLookup.DEFAULT.getHistogramFlavor(histogramFlavorPerMeter,
                createIdWithName("db.client.requests")))
            .isNull();
    }

    @Test
    void maxBucketsPerMeterLookup() {
        Map<String, Integer> maxBucketsPerMeter = new HashMap<>();
        maxBucketsPerMeter.put("http", 10);
        maxBucketsPerMeter.put("http.server", 20);

        assertThat(OtlpMeterRegistry.MaxBucketsPerMeterLookup.DEFAULT.getMaxBuckets(maxBucketsPerMeter,
                createIdWithName("http.server.requests")))
            .isEqualTo(20);
    }

    private Meter.Id createIdWithName(String name) {
        return new Meter.Id(name, Tags.empty(), null, null, Meter.Type.OTHER);
    }

    protected MetricData writeToMetric(Meter meter) {
        List<MetricData> metrics = writeToMetrics(meter);
        if (metrics.size() == 1) {
            return metrics.get(0);
        }
        return metrics.stream()
            .filter(m -> m.getType() != MetricDataType.DOUBLE_GAUGE)
            .findFirst()
            .orElse(metrics.get(0));
    }

    protected List<MetricData> writeToMetrics(Meter meter) {
        OtlpMetricConverter otlpMetricConverter = new OtlpMetricConverter(clock, otlpConfig().step(),
                registry.getBaseTimeUnit(), otlpConfig().aggregationTemporality(), registry.config().namingConvention(),
                otlpConfig().publishMaxGaugeForHistograms(), registry.getResource());
        otlpMetricConverter.addMeter(meter);
        return otlpMetricConverter.getAllMetrics();

    }

    protected List<MetricData> writeAllMeters() {
        OtlpMetricConverter otlpMetricConverter = new OtlpMetricConverter(clock, otlpConfig().step(),
                registry.getBaseTimeUnit(), otlpConfig().aggregationTemporality(), registry.config().namingConvention(),
                otlpConfig().publishMaxGaugeForHistograms(), registry.getResource());
        otlpMetricConverter.addMeters(registry.getMeters());
        return otlpMetricConverter.getAllMetrics();
    }

    protected void stepOverNStep(int numStepsToSkip) {
        clock.addSeconds(otlpConfig().step().toSeconds() * numStepsToSkip);
    }

    protected void assertHistogram(MetricData metric, long startTime, long endTime, String unit, long count, double sum,
            double max) {
        assertThat(metric.getHistogramData().getAggregationTemporality())
            .isEqualTo(AggregationTemporality.toOtlpAggregationTemporality(otlpConfig().aggregationTemporality()));

        HistogramPointData histogram = metric.getHistogramData().getPoints().iterator().next();
        assertMetricMetadata(metric, Optional.of(unit));
        assertThat(histogram.getStartEpochNanos()).isEqualTo(startTime);
        assertThat(histogram.getEpochNanos()).isEqualTo(endTime);
        assertThat(histogram.getCount()).isEqualTo(count);
        assertThat(histogram.getSum()).isEqualTo(sum);
        assertThat(histogram.getAttributes().size()).isEqualTo(1);
        assertThat(histogram.getAttributes().get(AttributeKey.stringKey(meterTag.getKey())))
            .isEqualTo(meterTag.getValue());

        if (!histogram.getBoundaries().isEmpty()) {
            assertThat(histogram.getCounts().stream().mapToLong(Long::longValue).sum()).isEqualTo(count);
            assertThat(histogram.getBoundaries().size() + 1).isEqualTo(histogram.getCounts().size());
        }

        if (otlpConfig().aggregationTemporality() == AggregationTemporality.DELTA) {
            assertThat(histogram.getMax()).isEqualTo(max);
        }
    }

    protected void assertSum(MetricData metric, long startTime, long endTime, double expectedValue) {
        DoublePointData sumDataPoint = metric.getDoubleSumData().getPoints().iterator().next();
        assertMetricMetadata(metric, Optional.empty());
        assertThat(sumDataPoint.getStartEpochNanos()).isEqualTo(startTime);
        assertThat(sumDataPoint.getEpochNanos()).isEqualTo(endTime);
        assertThat(sumDataPoint.getValue()).isEqualTo(expectedValue);
        assertThat(sumDataPoint.getAttributes().size()).isEqualTo(1);
        assertThat(sumDataPoint.getAttributes().get(AttributeKey.stringKey(meterTag.getKey())))
            .isEqualTo(meterTag.getValue());
        assertThat(metric.getDoubleSumData().getAggregationTemporality())
            .isEqualTo(AggregationTemporality.toOtlpAggregationTemporality(otlpConfig().aggregationTemporality()));
    }

    protected void assertExponentialHistogram(MetricData metric, long count, double sum, double max, long zeroCount,
            long scale) {
        assertThat(metric.getExponentialHistogramData().getPoints()).isNotEmpty();
        ExponentialHistogramPointData exponentialHistogramDataPoint = metric.getExponentialHistogramData()
            .getPoints()
            .iterator()
            .next();
        assertThat(exponentialHistogramDataPoint.getCount()).isEqualTo(count);
        assertThat(exponentialHistogramDataPoint.getSum()).isEqualTo(sum);
        assertThat(exponentialHistogramDataPoint.getMax()).isEqualTo(max);

        assertThat(exponentialHistogramDataPoint.getScale()).isEqualTo((int) scale);
        assertThat(exponentialHistogramDataPoint.getZeroCount()).isEqualTo(zeroCount);
        assertThat(exponentialHistogramDataPoint.getNegativeBuckets().getBucketCounts()).isEmpty();
    }

    private void assertMetricMetadata(final MetricData metric, Optional<String> unitOptional) {
        assertThat(metric.getName()).isEqualTo(METER_NAME);
        assertThat(metric.getDescription()).isEqualTo(METER_DESCRIPTION);
        unitOptional.ifPresent(unit -> assertThat(metric.getUnit()).isEqualTo(unit));
    }

    private void assertMaxGaugeMetrics(List<MetricData> metrics) {
        if (otlpConfig().publishMaxGaugeForHistograms()) {
            assertThat(metrics).filteredOn(m -> m.getType() == MetricDataType.DOUBLE_GAUGE).hasSize(1);
        }
    }

    abstract void testMetricsStartAndEndTime();

}
