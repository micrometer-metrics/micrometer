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

import com.google.protobuf.ByteString;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.ipc.http.HttpSender;
import io.opentelemetry.proto.metrics.v1.*;
import org.apache.commons.codec.binary.Hex;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static io.micrometer.registry.otlp.HistogramFlavor.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM;
import static io.micrometer.registry.otlp.HistogramFlavor.EXPLICIT_BUCKET_HISTOGRAM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.*;
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

    protected MockClock clock;

    protected ExemplarTestRecorder.TestExemplarContextProvider contextProvider;

    protected ExemplarTestRecorder recorder;

    private HttpSender mockHttpSender;

    OtlpMeterRegistry registry;

    OtlpMeterRegistry registryWithExponentialHistogram;

    abstract OtlpConfig otlpConfig();

    abstract OtlpConfig exponentialHistogramOtlpConfig();

    @BeforeEach
    void setUp() {
        this.clock = new MockClock();
        OtlpConfig config = otlpConfig();
        this.mockHttpSender = mock(HttpSender.class);
        OtlpMetricsSender metricsSender = new OtlpHttpMetricsSender(mockHttpSender);
        this.contextProvider = new ExemplarTestRecorder.TestExemplarContextProvider();
        this.recorder = new ExemplarTestRecorder(contextProvider, clock);
        this.registry = OtlpMeterRegistry.builder(config)
            .clock(clock)
            .metricsSender(metricsSender)
            .exemplarContextProvider(contextProvider)
            .build();
        this.registryWithExponentialHistogram = OtlpMeterRegistry.builder(exponentialHistogramOtlpConfig())
            .clock(clock)
            .metricsSender(metricsSender)
            .exemplarContextProvider(contextProvider)
            .build();
    }

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
            public @Nullable String get(String key) {
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

        assertThat(registry.getResourceAttributes())
            .noneMatch(keyValue -> keyValue.getValue().getStringValue().equals("no"));
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

    @Issue("#5577")
    @Test
    void httpHeaders() throws Throwable {
        HttpSender.Request.Builder builder = HttpSender.Request.build(otlpConfig().url(), this.mockHttpSender);
        when(mockHttpSender.post(otlpConfig().url())).thenReturn(builder);

        when(mockHttpSender.send(isA(HttpSender.Request.class))).thenReturn(new HttpSender.Response(200, ""));

        writeToMetric(TimeGauge.builder("gauge.time", this, TimeUnit.MICROSECONDS, o -> 24).register(registry));
        registry.publish();

        verify(this.mockHttpSender).send(assertArg(request -> {
            assertThat(request.getRequestHeaders().get("User-Agent")).startsWith("Micrometer-OTLP-Exporter-Java");
            assertThat(request.getRequestHeaders()).containsEntry("Content-Type", "application/x-protobuf");
        }));
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

        OtlpMetricsSender mockMetricsSender = mock(OtlpMetricsSender.class);
        OtlpMeterRegistry registryWithCompression = OtlpMeterRegistry.builder(configWithCompressionOn)
            .clock(clock)
            .metricsSender(mockMetricsSender)
            .build();

        Counter.builder("test.counter").register(registryWithCompression).increment();
        registryWithCompression.publish();

        verify(mockMetricsSender).send(assertArg(request -> {
            assertThat(request.getCompressionMode()).isEqualTo(CompressionMode.GZIP);
        }));
    }

    @Test
    void counterShouldWriteExemplars() {
        Counter counter = Counter.builder("test.counter").register(registry);
        Exemplar exemplar = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                () -> counter.increment(3), 3);
        stepOverNStep(1);

        assertThat(writeToMetrics(counter)).singleElement().satisfies(metric -> {
            assertThat(metric.getSum().getDataPoints(0).getExemplarsList()).singleElement().isEqualTo(exemplar);
        });
    }

    @Test
    void counterShouldRollOverExemplars() {
        Counter counter = Counter.builder("test.counter").register(registry);
        Exemplar exemplar = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                () -> counter.increment(3), 3);
        registry.close();

        assertThat(writeToMetrics(counter)).singleElement().satisfies(metric -> {
            assertThat(metric.getSum().getDataPoints(0).getExemplarsList()).singleElement().isEqualTo(exemplar);
        });
    }

    @RepeatedTest(10)
    void multipleCounterRecordingsShouldBeRandomlySampled() {
        Counter counter = Counter.builder("test.counter").register(registry);
        recorder.recordRandomMeasurements(5, counter::increment);
        stepOverNStep(1);

        assertThat(writeToMetrics(counter)).singleElement().satisfies(metric -> {
            assertThat(metric.getSum().getDataPointsList()).hasSize(1);
            assertThat(metric.getSum().getDataPoints(0).getExemplarsList()).doesNotHaveDuplicates()
                .hasSizeBetween(1, 5);
        });
    }

    @Test
    void distributionWithoutHistogramShouldWriteExemplars() {
        Timer timer = Timer.builder("timer").description(METER_DESCRIPTION).tags(Tags.of(meterTag)).register(registry);
        Exemplar exemplar1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                () -> timer.record(Duration.ofMillis(42)), 42);

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .register(registry);
        Exemplar exemplar2 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                () -> ds.record(44), 44);
        stepOverNStep(1);

        assertThat(writeToMetrics(timer)).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getHistogram().getDataPoints(0).getExemplarsList()).singleElement().isEqualTo(exemplar1);
        });

        assertThat(writeToMetrics(ds)).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getHistogram().getDataPoints(0).getExemplarsList()).singleElement().isEqualTo(exemplar2);
        });
    }

    @Test
    void distributionWithoutHistogramShouldRollOverExemplars() {
        Timer timer = Timer.builder("timer").description(METER_DESCRIPTION).tags(Tags.of(meterTag)).register(registry);
        Exemplar exemplar1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                () -> timer.record(Duration.ofMillis(42)), 42);

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .register(registry);
        Exemplar exemplar2 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                () -> ds.record(44), 44);

        registry.close();

        assertThat(writeToMetrics(timer)).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getHistogram().getDataPoints(0).getExemplarsList()).singleElement().isEqualTo(exemplar1);
        });

        assertThat(writeToMetrics(ds)).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getHistogram().getDataPoints(0).getExemplarsList()).singleElement().isEqualTo(exemplar2);
        });
    }

    @RepeatedTest(10)
    void multipleDistributionsWithoutHistogramRecordingsShouldBeRandomlySampled() {
        Timer timer = Timer.builder("timer").description(METER_DESCRIPTION).tags(Tags.of(meterTag)).register(registry);
        recorder.recordRandomMeasurements(5, index -> timer.record(Duration.ofMillis(index)));

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .register(registry);
        recorder.recordRandomMeasurements(5, ds::record);
        stepOverNStep(1);

        assertThat(writeToMetrics(timer)).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getHistogram().getDataPoints(0).getExemplarsList()).singleElement()
                .satisfies(exemplar -> assertThat(exemplar.getAsDouble()).isBetween(1.0, 5.0));
        });

        assertThat(writeToMetrics(ds)).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getHistogram().getDataPoints(0).getExemplarsList()).singleElement()
                .satisfies(exemplar -> assertThat(exemplar.getAsDouble()).isBetween(1.0, 5.0));
        });
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

        List<Metric> timerMetrics = writeToMetrics(timer.register(registry));
        assertThat(timerMetrics).filteredOn(Metric::hasSummary).singleElement().satisfies(summary -> {
            assertThat(summary.getDataCase().getNumber()).isEqualTo(Metric.DataCase.SUMMARY.getNumber());
        });
        assertMaxGaugeMetrics(timerMetrics);
        List<Metric> dsMetrics = writeToMetrics(ds.register(registry));
        assertThat(dsMetrics).filteredOn(Metric::hasSummary).singleElement().satisfies(summary -> {
            assertThat(summary.getDataCase().getNumber()).isEqualTo(Metric.DataCase.SUMMARY.getNumber());
        });
        assertMaxGaugeMetrics(dsMetrics);
        List<Metric> timerExpoMetrics = writeToMetrics(timer.register(registryWithExponentialHistogram));
        assertThat(timerExpoMetrics).filteredOn(Metric::hasSummary).singleElement().satisfies(summary -> {
            assertThat(summary.getDataCase().getNumber()).isEqualTo(Metric.DataCase.SUMMARY.getNumber());
        });
        assertMaxGaugeMetrics(timerExpoMetrics);
        List<Metric> dsExpoMetrics = writeToMetrics(ds.register(registryWithExponentialHistogram));
        assertThat(dsExpoMetrics).filteredOn(Metric::hasSummary).singleElement().satisfies(summary -> {
            assertThat(summary.getDataCase().getNumber()).isEqualTo(Metric.DataCase.SUMMARY.getNumber());
        });
        assertMaxGaugeMetrics(dsExpoMetrics);
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

        List<Metric> timerMetrics = writeToMetrics(timer.register(registry));
        assertThat(timerMetrics).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getDataCase().getNumber()).isEqualTo(Metric.DataCase.HISTOGRAM.getNumber());
        });
        assertMaxGaugeMetrics(timerMetrics);
        List<Metric> dsMetrics = writeToMetrics(ds.register(registry));
        assertThat(dsMetrics).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getDataCase().getNumber()).isEqualTo(Metric.DataCase.HISTOGRAM.getNumber());
        });
        assertMaxGaugeMetrics(dsMetrics);
        List<Metric> timerExpoMetrics = writeToMetrics(timer.register(registryWithExponentialHistogram));
        assertThat(timerExpoMetrics).filteredOn(Metric::hasExponentialHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getDataCase().getNumber()).isEqualTo(Metric.DataCase.EXPONENTIAL_HISTOGRAM.getNumber());
        });
        assertMaxGaugeMetrics(timerExpoMetrics);
        List<Metric> dsExpoMetrics = writeToMetrics(ds.register(registryWithExponentialHistogram));
        assertThat(dsExpoMetrics).filteredOn(Metric::hasExponentialHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getDataCase().getNumber()).isEqualTo(Metric.DataCase.EXPONENTIAL_HISTOGRAM.getNumber());
        });
        assertMaxGaugeMetrics(dsExpoMetrics);
    }

    @Test
    void distributionWithPercentileHistogramShouldWriteExemplars() {
        Timer timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registry);

        Exemplar exemplar1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                () -> timer.record(Duration.ofMillis(42)), 42);

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registry);

        Exemplar exemplar2 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                () -> ds.record(44), 44);
        stepOverNStep(1);

        assertThat(writeToMetrics(timer)).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getHistogram().getDataPoints(0).getExemplarsList()).singleElement().isEqualTo(exemplar1);
        });

        assertThat(writeToMetrics(ds)).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getHistogram().getDataPoints(0).getExemplarsList()).singleElement().isEqualTo(exemplar2);
        });
    }

    @Test
    void distributionWithExponentialHistogramShouldWriteExemplars() {
        Timer timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registryWithExponentialHistogram);

        Exemplar exemplar1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                () -> timer.record(Duration.ofMillis(42)), 42);

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registryWithExponentialHistogram);

        Exemplar exemplar2 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                () -> ds.record(44), 44);
        stepOverNStep(1);

        assertThat(writeToMetrics(timer)).filteredOn(Metric::hasExponentialHistogram)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getExponentialHistogram().getDataPointsList()).hasSize(1);
                assertThat(metric.getExponentialHistogram().getDataPoints(0).getExemplarsList()).singleElement()
                    .isEqualTo(exemplar1);
            });

        assertThat(writeToMetrics(ds)).filteredOn(Metric::hasExponentialHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getExponentialHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getExponentialHistogram().getDataPoints(0).getExemplarsList()).singleElement()
                .isEqualTo(exemplar2);
        });
    }

    @Test
    void distributionWithPercentileHistogramShouldRollOverExemplars() {
        Timer timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registry);

        Exemplar exemplar1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                () -> timer.record(Duration.ofMillis(42)), 42);

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registry);

        Exemplar exemplar2 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                () -> ds.record(44), 44);

        registry.close();

        assertThat(writeToMetrics(timer)).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getHistogram().getDataPoints(0).getExemplarsList()).singleElement().isEqualTo(exemplar1);
        });

        assertThat(writeToMetrics(ds)).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getHistogram().getDataPoints(0).getExemplarsList()).singleElement().isEqualTo(exemplar2);
        });
    }

    @Test
    void distributionWithExponentialHistogramShouldRollOverExemplars() {
        Timer timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registryWithExponentialHistogram);

        Exemplar exemplar1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                () -> timer.record(Duration.ofMillis(42)), 42);

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registryWithExponentialHistogram);

        Exemplar exemplar2 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                () -> ds.record(44), 44);

        registryWithExponentialHistogram.close();

        assertThat(writeToMetrics(timer)).filteredOn(Metric::hasExponentialHistogram)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getExponentialHistogram().getDataPointsList()).hasSize(1);
                assertThat(metric.getExponentialHistogram().getDataPoints(0).getExemplarsList()).singleElement()
                    .isEqualTo(exemplar1);
            });

        assertThat(writeToMetrics(ds)).filteredOn(Metric::hasExponentialHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getExponentialHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getExponentialHistogram().getDataPoints(0).getExemplarsList()).singleElement()
                .isEqualTo(exemplar2);
        });
    }

    @Test
    void multipleDistributionsWithPercentileHistogramShouldWriteBucketedExemplars() {
        Timer timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registry);

        // relevant buckets: 1.0, 89.478485, 111.848106, 30000.0, +Inf
        Exemplar exemplar1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                () -> timer.record(Duration.ofMillis(1)), 1);
        recorder.record("4bf92f3577b34da6a3ce929d0e000002", "00f067aa0b000002",
                () -> timer.record(Duration.ofMillis(100)), 100);
        // falls into the same bucket as the previous and it overwrites it
        Exemplar exemplar2 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                () -> timer.record(Duration.ofMillis(110)), 110);
        Exemplar exemplar3 = recorder.record("4bf92f3577b34da6a3ce929d0e000004", "00f067aa0b000004",
                () -> timer.record(Duration.ofSeconds(30)), 30_000);
        recorder.record("4bf92f3577b34da6a3ce929d0e000005", "00f067aa0b000005",
                () -> timer.record(Duration.ofSeconds(31)), 31_000);
        // falls into the same bucket as the previous and it overwrites it
        Exemplar exemplar4 = recorder.record("4bf92f3577b34da6a3ce929d0e000006", "00f067aa0b000006",
                () -> timer.record(Duration.ofSeconds(42)), 42_000);

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registry);

        // relevant buckets: 1.0, 85.0, 106.0, 4.2273788502251054E18, +Inf
        Exemplar exemplar5 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001", () -> ds.record(1),
                1);
        recorder.record("4bf92f3577b34da6a3ce929d0e000002", "00f067aa0b000002", () -> ds.record(90), 90);
        // falls into the same bucket as the previous and it overwrites it
        Exemplar exemplar6 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                () -> ds.record(100), 100);
        Exemplar exemplar7 = recorder.record("4bf92f3577b34da6a3ce929d0e000004", "00f067aa0b000004",
                () -> ds.record(4.2E18), 4.2E18);
        recorder.record("4bf92f3577b34da6a3ce929d0e000005", "00f067aa0b000005", () -> ds.record(4.3E18), 4.3E18);
        // falls into the same bucket as the previous and it overwrites it
        Exemplar exemplar8 = recorder.record("4bf92f3577b34da6a3ce929d0e000006", "00f067aa0b000006",
                () -> ds.record(4.4E18), 4.4E18);

        stepOverNStep(1);

        assertThat(writeToMetrics(timer)).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getHistogram().getDataPoints(0).getExemplarsList()).hasSize(4)
                .containsExactly(exemplar1, exemplar2, exemplar3, exemplar4);
        });

        assertThat(writeToMetrics(ds)).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getHistogram().getDataPoints(0).getExemplarsList()).hasSize(4)
                .containsExactly(exemplar5, exemplar6, exemplar7, exemplar8);
        });
    }

    @RepeatedTest(10)
    void multipleDistributionsWithExponentialHistogramShouldWriteRandomlySampledExemplars() {
        Timer timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registryWithExponentialHistogram);
        recorder.recordRandomMeasurements(5, index -> timer.record(Duration.ofMillis(index)));

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentileHistogram()
            .register(registryWithExponentialHistogram);
        recorder.recordRandomMeasurements(5, ds::record);

        stepOverNStep(1);

        assertThat(writeToMetrics(timer)).filteredOn(Metric::hasExponentialHistogram)
            .singleElement()
            .satisfies(metric -> {
                assertThat(metric.getExponentialHistogram().getDataPointsList()).hasSize(1);
                assertThat(metric.getExponentialHistogram().getDataPoints(0).getExemplarsList()).doesNotHaveDuplicates()
                    .hasSizeBetween(1, 5);
            });

        assertThat(writeToMetrics(ds)).filteredOn(Metric::hasExponentialHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getExponentialHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getExponentialHistogram().getDataPoints(0).getExemplarsList()).doesNotHaveDuplicates()
                .hasSizeBetween(1, 5);
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

        List<Metric> metrics = writeAllMeters();
        if (otlpConfig().publishMaxGaugeForHistograms()) {
            assertThat(metrics).hasSize(4);
            assertThat(metrics).filteredOn(Metric::hasGauge).hasSize(2).satisfiesExactlyInAnyOrder(metric -> {
                assertThat(metric.getDescription()).isEqualTo("description");
                assertThat(metric.getGauge().getDataPointsCount()).isEqualTo(2);
            }, metric -> {
                assertThat(metric.getDescription()).isEqualTo("description");
                assertThat(metric.getGauge().getDataPointsCount()).isEqualTo(2);
                assertThat(metric.getUnit()).isEqualTo("milliseconds");
            });
        }
        else {
            assertThat(metrics).hasSize(3);
            assertThat(metrics).filteredOn(Metric::hasGauge).singleElement().satisfies(metric -> {
                assertThat(metric.getDescription()).isEqualTo("description");
                assertThat(metric.getGauge().getDataPointsCount()).isEqualTo(2);
            });
        }

        assertThat(metrics).filteredOn(Metric::hasSum).singleElement().satisfies(metric -> {
            assertThat(metric.getDescription()).isEqualTo("description");
            assertThat(metric.getSum().getDataPointsCount()).isEqualTo(2);
            assertThat(metric.getSum().getAggregationTemporality())
                .isEqualTo(AggregationTemporality.toOtlpAggregationTemporality(otlpConfig().aggregationTemporality()));
        });

        assertThat(metrics).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getDescription()).isEqualTo("description");
            assertThat(metric.getHistogram().getDataPointsCount()).isEqualTo(2);
            assertThat(metric.getHistogram().getAggregationTemporality())
                .isEqualTo(AggregationTemporality.toOtlpAggregationTemporality(otlpConfig().aggregationTemporality()));
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

        List<Metric> metrics = writeAllMeters();
        if (otlpConfig().publishMaxGaugeForHistograms()) {
            assertThat(metrics).hasSize(8);
            assertThat(metrics).filteredOn(Metric::hasGauge)
                .hasSize(4)
                .satisfiesExactlyInAnyOrder(metric -> assertThat(metric.getDescription()).isEqualTo(description1),
                        metric -> assertThat(metric.getDescription()).isEqualTo(description2),
                        metric -> assertThat(metric.getDescription()).isEqualTo(description1),
                        metric -> assertThat(metric.getDescription()).isEqualTo(description2));
        }
        else {
            assertThat(metrics).hasSize(6);
            assertThat(metrics).filteredOn(Metric::hasGauge)
                .hasSize(2)
                .satisfiesExactlyInAnyOrder(metric -> assertThat(metric.getDescription()).isEqualTo(description1),
                        metric -> assertThat(metric.getDescription()).isEqualTo(description2));

        }

        assertThat(metrics).filteredOn(Metric::hasSum)
            .hasSize(2)
            .satisfiesExactlyInAnyOrder(metric -> assertThat(metric.getUnit()).isEmpty(),
                    metric -> assertThat(metric.getUnit()).isEqualTo("xyz"));

        assertThat(metrics).filteredOn(Metric::hasHistogram)
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

        List<Metric> timerMetrics = writeToMetrics(timer.register(registry));
        assertThat(timerMetrics).filteredOn(Metric::hasHistogram)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getDataCase().getNumber())
                .isEqualTo(Metric.DataCase.HISTOGRAM.getNumber()));
        assertMaxGaugeMetrics(timerMetrics);
        List<Metric> dsMetrics = writeToMetrics(ds.register(registry));
        assertThat(dsMetrics).filteredOn(Metric::hasHistogram)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getDataCase().getNumber())
                .isEqualTo(Metric.DataCase.HISTOGRAM.getNumber()));
        assertMaxGaugeMetrics(dsMetrics);
        List<Metric> timerExpoMetrics = writeToMetrics(timer.register(registryWithExponentialHistogram));
        assertThat(timerExpoMetrics).filteredOn(Metric::hasExponentialHistogram)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getDataCase().getNumber())
                .isEqualTo(Metric.DataCase.EXPONENTIAL_HISTOGRAM.getNumber()));
        assertMaxGaugeMetrics(timerExpoMetrics);
        List<Metric> dsExpoMetrics = writeToMetrics(ds.register(registryWithExponentialHistogram));
        assertThat(dsExpoMetrics).filteredOn(Metric::hasExponentialHistogram)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getDataCase().getNumber())
                .isEqualTo(Metric.DataCase.EXPONENTIAL_HISTOGRAM.getNumber()));
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

        Exemplar exemplar1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                () -> timer.record(Duration.ofMillis(42)), 42);

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .publishPercentiles(0.5, 0.9)
            .publishPercentileHistogram()
            .register(registry);

        Exemplar exemplar2 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                () -> ds.record(44), 44);

        stepOverNStep(1);

        assertThat(writeToMetrics(timer)).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getHistogram().getDataPoints(0).getExemplarsList()).singleElement().isEqualTo(exemplar1);
        });

        assertThat(writeToMetrics(ds)).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getHistogram().getDataPoints(0).getExemplarsList()).singleElement().isEqualTo(exemplar2);
        });
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

        List<Metric> timerMetrics = writeToMetrics(timer.register(registry));
        assertThat(timerMetrics).filteredOn(Metric::hasHistogram)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getDataCase().getNumber())
                .isEqualTo(Metric.DataCase.HISTOGRAM.getNumber()));
        assertMaxGaugeMetrics(timerMetrics);
        List<Metric> dsMetrics = writeToMetrics(ds.register(registry));
        assertThat(dsMetrics).filteredOn(Metric::hasHistogram)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getDataCase().getNumber())
                .isEqualTo(Metric.DataCase.HISTOGRAM.getNumber()));
        assertMaxGaugeMetrics(dsMetrics);
        List<Metric> timerExpoMetrics = writeToMetrics(timer.register(registryWithExponentialHistogram));
        assertThat(timerExpoMetrics).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getDataCase().getNumber()).isEqualTo(Metric.DataCase.HISTOGRAM.getNumber());
        });
        assertMaxGaugeMetrics(timerExpoMetrics);
        List<Metric> dsExpoMetrics = writeToMetrics(ds.register(registryWithExponentialHistogram));
        assertThat(dsExpoMetrics).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getDataCase().getNumber()).isEqualTo(Metric.DataCase.HISTOGRAM.getNumber());
        });
        assertMaxGaugeMetrics(dsExpoMetrics);
    }

    @Test
    void distributionWithSLOSShouldWriteExemplars() {
        Timer timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .serviceLevelObjectives(Duration.ofMillis(1))
            .register(registry);

        Exemplar exemplar1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                () -> timer.record(Duration.ofMillis(42)), 42);

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .serviceLevelObjectives(1.0)
            .register(registry);

        Exemplar exemplar2 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                () -> ds.record(44), 44);

        stepOverNStep(1);

        assertThat(writeToMetrics(timer)).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getHistogram().getDataPoints(0).getExemplarsList()).singleElement().isEqualTo(exemplar1);
        });

        assertThat(writeToMetrics(ds)).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getHistogram().getDataPoints(0).getExemplarsList()).singleElement().isEqualTo(exemplar2);
        });
    }

    @Test
    void distributionWithSLOSShouldRollOverExemplars() {
        Timer timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .serviceLevelObjectives(Duration.ofMillis(1))
            .register(registry);

        Exemplar exemplar1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                () -> timer.record(Duration.ofMillis(42)), 42);

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .serviceLevelObjectives(1.0)
            .register(registry);

        Exemplar exemplar2 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                () -> ds.record(44), 44);

        registry.close();

        assertThat(writeToMetrics(timer)).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getHistogram().getDataPoints(0).getExemplarsList()).singleElement().isEqualTo(exemplar1);
        });

        assertThat(writeToMetrics(ds)).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getHistogram().getDataPoints(0).getExemplarsList()).singleElement().isEqualTo(exemplar2);
        });
    }

    @Test
    void multipleDistributionsWithSLOSShouldWriteBucketedExemplars() {
        Timer timer = Timer.builder("timer")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .serviceLevelObjectives(Duration.ofMillis(1), Duration.ofMillis(110), Duration.ofSeconds(1))
            .register(registry);

        Exemplar exemplar1 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001",
                () -> timer.record(Duration.ofMillis(1)), 1);
        recorder.record("4bf92f3577b34da6a3ce929d0e000002", "00f067aa0b000002",
                () -> timer.record(Duration.ofMillis(100)), 100);
        // falls into the same bucket as the previous and it overwrites it
        Exemplar exemplar2 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                () -> timer.record(Duration.ofMillis(110)), 110);
        Exemplar exemplar3 = recorder.record("4bf92f3577b34da6a3ce929d0e000004", "00f067aa0b000004",
                () -> timer.record(Duration.ofSeconds(1)), 1_000);
        recorder.record("4bf92f3577b34da6a3ce929d0e000005", "00f067aa0b000005",
                () -> timer.record(Duration.ofSeconds(2)), 2_000);
        // falls into the same bucket as the previous and it overwrites it
        Exemplar exemplar4 = recorder.record("4bf92f3577b34da6a3ce929d0e000006", "00f067aa0b000006",
                () -> timer.record(Duration.ofSeconds(3)), 3_000);

        DistributionSummary ds = DistributionSummary.builder("ds")
            .description(METER_DESCRIPTION)
            .tags(Tags.of(meterTag))
            .serviceLevelObjectives(1.0, 110, 1_000, 3_000)
            .register(registry);

        // relevant buckets: 1.0, 85.0, 106.0, 4.2273788502251054E18, +Inf
        Exemplar exemplar5 = recorder.record("4bf92f3577b34da6a3ce929d0e000001", "00f067aa0b000001", () -> ds.record(1),
                1);
        recorder.record("4bf92f3577b34da6a3ce929d0e000002", "00f067aa0b000002", () -> ds.record(90), 90);
        // falls into the same bucket as the previous and it overwrites it
        Exemplar exemplar6 = recorder.record("4bf92f3577b34da6a3ce929d0e000003", "00f067aa0b000003",
                () -> ds.record(100), 100);
        Exemplar exemplar7 = recorder.record("4bf92f3577b34da6a3ce929d0e000004", "00f067aa0b000004",
                () -> ds.record(1_000), 1_000);
        recorder.record("4bf92f3577b34da6a3ce929d0e000005", "00f067aa0b000005", () -> ds.record(2_000), 2_000);
        // falls into the same bucket as the previous and it overwrites it
        Exemplar exemplar8 = recorder.record("4bf92f3577b34da6a3ce929d0e000006", "00f067aa0b000006",
                () -> ds.record(3_000), 3_000);

        stepOverNStep(1);

        assertThat(writeToMetrics(timer)).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getHistogram().getDataPoints(0).getExemplarsList()).hasSize(4)
                .containsExactly(exemplar1, exemplar2, exemplar3, exemplar4);
        });

        assertThat(writeToMetrics(ds)).filteredOn(Metric::hasHistogram).singleElement().satisfies(metric -> {
            assertThat(metric.getHistogram().getDataPointsList()).hasSize(1);
            assertThat(metric.getHistogram().getDataPoints(0).getExemplarsList()).hasSize(4)
                .containsExactly(exemplar5, exemplar6, exemplar7, exemplar8);
        });
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

        assertThat(writeToMetrics(timerWithZero1ms)).filteredOn(Metric::hasExponentialHistogram)
            .singleElement()
            .satisfies(exponentialHistogram -> {
                ExponentialHistogramDataPoint dataPoint = exponentialHistogram.getExponentialHistogram()
                    .getDataPoints(0);
                assertThat(dataPoint.getZeroCount()).isEqualTo(1);
                assertThat(dataPoint.getCount()).isEqualTo(2);
                assertThat(dataPoint.getPositive().getBucketCountsCount()).isEqualTo(1);
                assertThat(exponentialHistogram.getDataCase().getNumber())
                    .isEqualTo(Metric.DataCase.EXPONENTIAL_HISTOGRAM.getNumber());
            });

        assertThat(writeToMetrics(timerWithZero1ns)).filteredOn(Metric::hasExponentialHistogram)
            .singleElement()
            .satisfies(exponentialHistogram -> {
                ExponentialHistogramDataPoint dataPoint = exponentialHistogram.getExponentialHistogram()
                    .getDataPoints(0);
                assertThat(dataPoint.getZeroCount()).isZero();
                assertThat(dataPoint.getCount()).isEqualTo(2);
                assertThat(dataPoint.getPositive().getBucketCountsCount()).isGreaterThan(1);
            });
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
        List<Metric> metrics = writeToMetrics(timer);
        if (otlpConfig().publishMaxGaugeForHistograms()) {
            assertThat(metrics).hasSize(2);
        }
        else {
            assertThat(metrics).hasSize(1);
        }

        assertThat(metrics).filteredOn(Metric::hasExponentialHistogram)
            .singleElement()
            .satisfies(exponentialHistogram -> {
                ExponentialHistogramDataPoint dataPoint = exponentialHistogram.getExponentialHistogram()
                    .getDataPoints(0);

                assertThat(dataPoint.getCount()).isEqualTo(3);
                assertThat(dataPoint.getSum()).isEqualTo(1001.001);

                ExponentialHistogramDataPoint.Buckets buckets = dataPoint.getPositive();
                assertThat(buckets.getOffset()).isEqualTo(-80);
                assertThat(buckets.getBucketCountsCount()).isEqualTo(160);
                assertThat(buckets.getBucketCountsList().get(0)).isEqualTo(1);
                assertThat(buckets.getBucketCountsList().get(79)).isEqualTo(1);
                assertThat(buckets.getBucketCountsList().get(159)).isEqualTo(1);
                assertThat(buckets.getBucketCountsList()).filteredOn(v -> v == 0).hasSize(157);
            });
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

    @Test
    void defaultHistogramFlavorShouldBeUsedIfNoOverrides() {
        OtlpConfig config = new OtlpConfig() {
            @Override
            public @Nullable String get(String key) {
                return null;
            }

            @Override
            public AggregationTemporality aggregationTemporality() {
                return otlpConfig().aggregationTemporality();
            }
        };
        OtlpMeterRegistry meterRegistry = OtlpMeterRegistry.builder(config).clock(clock).build();

        Timer timer = Timer.builder("test.timer").publishPercentileHistogram().register(meterRegistry);

        assertThat(writeToMetrics(timer)).filteredOn(Metric::hasHistogram)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getDataCase().getNumber())
                .isEqualTo(Metric.DataCase.HISTOGRAM.getNumber()));

        meterRegistry.clear();

        DistributionSummary ds = DistributionSummary.builder("test.ds")
            .publishPercentileHistogram()
            .register(meterRegistry);
        assertThat(writeToMetrics(ds)).filteredOn(Metric::hasHistogram)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getDataCase().getNumber())
                .isEqualTo(Metric.DataCase.HISTOGRAM.getNumber()));
    }

    @Test
    void globalHistogramFlavorShouldBeUsedIfNoPerMeterConfig() {
        OtlpConfig config = new OtlpConfig() {
            @Override
            public @Nullable String get(String key) {
                return null;
            }

            @Override
            public AggregationTemporality aggregationTemporality() {
                return otlpConfig().aggregationTemporality();
            }

            @Override
            public HistogramFlavor histogramFlavor() {
                return BASE2_EXPONENTIAL_BUCKET_HISTOGRAM;
            }
        };
        OtlpMeterRegistry meterRegistry = OtlpMeterRegistry.builder(config).clock(clock).build();
        Timer timer = Timer.builder("test.timer").publishPercentileHistogram().register(meterRegistry);

        assertThat(writeToMetrics(timer)).filteredOn(Metric::hasExponentialHistogram)
            .singleElement()
            .satisfies(exponentialHistogram -> {
                assertThat(exponentialHistogram.getDataCase().getNumber())
                    .isEqualTo(Metric.DataCase.EXPONENTIAL_HISTOGRAM.getNumber());
            });

        meterRegistry.clear();

        DistributionSummary ds = DistributionSummary.builder("test.ds")
            .publishPercentileHistogram()
            .register(meterRegistry);

        assertThat(writeToMetrics(ds)).filteredOn(Metric::hasExponentialHistogram)
            .singleElement()
            .satisfies(exponentialHistogram -> {
                assertThat(exponentialHistogram.getDataCase().getNumber())
                    .isEqualTo(Metric.DataCase.EXPONENTIAL_HISTOGRAM.getNumber());
            });
    }

    @Test
    void perMeterHistogramFlavorShouldBeUsedFromConfigIfNoLookupOverrides() {
        OtlpConfig config = new OtlpConfig() {
            @Override
            public @Nullable String get(String key) {
                return null;
            }

            @Override
            public AggregationTemporality aggregationTemporality() {
                return otlpConfig().aggregationTemporality();
            }

            @Override
            public HistogramFlavor histogramFlavor() {
                return EXPLICIT_BUCKET_HISTOGRAM;
            }

            @Override
            public Map<String, HistogramFlavor> histogramFlavorPerMeter() {
                Map<String, HistogramFlavor> histogramFlavors = new HashMap<>();
                histogramFlavors.put("expo", BASE2_EXPONENTIAL_BUCKET_HISTOGRAM);
                return histogramFlavors;
            }
        };
        OtlpMeterRegistry meterRegistry = OtlpMeterRegistry.builder(config).clock(clock).build();

        Timer expo = Timer.builder("expo").publishPercentileHistogram().register(meterRegistry);
        Timer expoOther = Timer.builder("expo.other").publishPercentileHistogram().register(meterRegistry);
        Timer other = Timer.builder("other").publishPercentileHistogram().register(meterRegistry);
        assertThat(writeToMetrics(expo)).filteredOn(Metric::hasExponentialHistogram)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getDataCase().getNumber())
                .isEqualTo(Metric.DataCase.EXPONENTIAL_HISTOGRAM.getNumber()));
        assertThat(writeToMetrics(expoOther)).filteredOn(Metric::hasExponentialHistogram)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getDataCase().getNumber())
                .isEqualTo(Metric.DataCase.EXPONENTIAL_HISTOGRAM.getNumber()));
        assertThat(writeToMetrics(other)).filteredOn(Metric::hasHistogram)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getDataCase().getNumber())
                .isEqualTo(Metric.DataCase.HISTOGRAM.getNumber()));

        meterRegistry.clear();

        DistributionSummary expo2 = DistributionSummary.builder("expo")
            .publishPercentileHistogram()
            .register(meterRegistry);
        DistributionSummary expoOther2 = DistributionSummary.builder("expo.other")
            .publishPercentileHistogram()
            .register(meterRegistry);
        DistributionSummary other2 = DistributionSummary.builder("other")
            .publishPercentileHistogram()
            .register(meterRegistry);
        assertThat(writeToMetrics(expo2)).filteredOn(Metric::hasExponentialHistogram)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getDataCase().getNumber())
                .isEqualTo(Metric.DataCase.EXPONENTIAL_HISTOGRAM.getNumber()));
        assertThat(writeToMetrics(expoOther2)).filteredOn(Metric::hasExponentialHistogram)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getDataCase().getNumber())
                .isEqualTo(Metric.DataCase.EXPONENTIAL_HISTOGRAM.getNumber()));
        assertThat(writeToMetrics(other2)).filteredOn(Metric::hasHistogram)
            .singleElement()
            .satisfies(metric -> assertThat(metric.getDataCase().getNumber())
                .isEqualTo(Metric.DataCase.HISTOGRAM.getNumber()));
    }

    @Test
    void globalMaxBucketsShouldBeUsedIfNoPerMeterConfig() {
        OtlpConfig config = new OtlpConfig() {
            @Override
            public @Nullable String get(String key) {
                return null;
            }

            @Override
            public AggregationTemporality aggregationTemporality() {
                return otlpConfig().aggregationTemporality();
            }

            @Override
            public HistogramFlavor histogramFlavor() {
                return BASE2_EXPONENTIAL_BUCKET_HISTOGRAM;
            }

            @Override
            public int maxBucketCount() {
                return 56;
            }
        };
        OtlpMeterRegistry meterRegistry = OtlpMeterRegistry.builder(config).clock(clock).build();
        Timer timer = Timer.builder("test.timer").publishPercentileHistogram().register(meterRegistry);
        IntStream.range(1, 111).forEach(i -> timer.record(i, TimeUnit.MILLISECONDS));

        clock.add(config.step());

        assertThat(writeToMetrics(timer)).filteredOn(Metric::hasExponentialHistogram)
            .singleElement()
            .satisfies(exponentialHistogram -> {
                assertThat(exponentialHistogram.getExponentialHistogram()
                    .getDataPoints(0)
                    .getPositive()
                    .getBucketCountsList()).hasSize(56);
            });

        meterRegistry.clear();

        DistributionSummary ds = DistributionSummary.builder("test.ds")
            .publishPercentileHistogram()
            .register(meterRegistry);
        IntStream.range(1, 111).forEach(ds::record);

        clock.add(config.step());

        assertThat(writeToMetrics(ds)).filteredOn(Metric::hasExponentialHistogram)
            .singleElement()
            .satisfies(exponentialHistogram -> {
                ExponentialHistogramDataPoint dataPoint = exponentialHistogram.getExponentialHistogram()
                    .getDataPoints(0);
                assertThat(dataPoint.getPositive().getBucketCountsList()).hasSize(56);
            });
    }

    @Test
    void perMeterMaxBucketsShouldBeUsedFromConfigIfNoLookupOverrides() {
        OtlpConfig config = new OtlpConfig() {
            @Override
            public @Nullable String get(String key) {
                return null;
            }

            @Override
            public AggregationTemporality aggregationTemporality() {
                return otlpConfig().aggregationTemporality();
            }

            @Override
            public HistogramFlavor histogramFlavor() {
                return BASE2_EXPONENTIAL_BUCKET_HISTOGRAM;
            }

            @Override
            public int maxBucketCount() {
                return 56;
            }

            @Override
            public Map<String, Integer> maxBucketsPerMeter() {
                Map<String, Integer> maxBuckets = new HashMap<>();
                maxBuckets.put("low.variation", 15);
                return maxBuckets;
            }
        };
        OtlpMeterRegistry meterRegistry = OtlpMeterRegistry.builder(config).clock(clock).build();

        Timer lowVariation = Timer.builder("low.variation").publishPercentileHistogram().register(meterRegistry);
        Timer lowVariationOther = Timer.builder("low.variation.other")
            .publishPercentileHistogram()
            .register(meterRegistry);
        Timer other = Timer.builder("other").publishPercentileHistogram().register(meterRegistry);

        List.of(lowVariation, lowVariationOther, other)
            .forEach(t -> IntStream.range(1, 111).forEach(i -> t.record(i, TimeUnit.MILLISECONDS)));
        clock.add(config.step());

        assertThat(writeToMetrics(lowVariation)).filteredOn(Metric::hasExponentialHistogram)
            .singleElement()
            .satisfies(exponentialHistogram -> {
                ExponentialHistogramDataPoint dataPoint = exponentialHistogram.getExponentialHistogram()
                    .getDataPoints(0);
                assertThat(dataPoint.getPositive().getBucketCountsList()).hasSize(15);
            });
        assertThat(writeToMetrics(lowVariationOther)).filteredOn(Metric::hasExponentialHistogram)
            .singleElement()
            .satisfies(exponentialHistogram -> {
                ExponentialHistogramDataPoint dataPoint = exponentialHistogram.getExponentialHistogram()
                    .getDataPoints(0);
                assertThat(dataPoint.getPositive().getBucketCountsList()).hasSize(15);
            });

        assertThat(writeToMetrics(other)).filteredOn(Metric::hasExponentialHistogram)
            .singleElement()
            .satisfies(exponentialHistogram -> {
                ExponentialHistogramDataPoint dataPoint = exponentialHistogram.getExponentialHistogram()
                    .getDataPoints(0);
                assertThat(dataPoint.getPositive().getBucketCountsList()).hasSize(56);
            });

        meterRegistry.clear();

        DistributionSummary lowVariation2 = DistributionSummary.builder("low.variation")
            .publishPercentileHistogram()
            .register(meterRegistry);
        DistributionSummary lowVariationOther2 = DistributionSummary.builder("low.variation.other")
            .publishPercentileHistogram()
            .register(meterRegistry);
        DistributionSummary other2 = DistributionSummary.builder("other")
            .publishPercentileHistogram()
            .register(meterRegistry);

        List.of(lowVariation2, lowVariationOther2, other2).forEach(t -> IntStream.range(1, 111).forEach(t::record));
        clock.add(config.step());

        assertThat(writeToMetrics(lowVariation2)).filteredOn(Metric::hasExponentialHistogram)
            .singleElement()
            .satisfies(exponentialHistogram -> {
                ExponentialHistogramDataPoint dataPoint = exponentialHistogram.getExponentialHistogram()
                    .getDataPoints(0);
                assertThat(dataPoint.getPositive().getBucketCountsList()).hasSize(15);
            });
        assertThat(writeToMetrics(lowVariationOther2)).filteredOn(Metric::hasExponentialHistogram)
            .singleElement()
            .satisfies(exponentialHistogram -> {
                ExponentialHistogramDataPoint dataPoint = exponentialHistogram.getExponentialHistogram()
                    .getDataPoints(0);
                assertThat(dataPoint.getPositive().getBucketCountsList()).hasSize(15);
            });
        assertThat(writeToMetrics(other2)).filteredOn(Metric::hasExponentialHistogram)
            .singleElement()
            .satisfies(exponentialHistogram -> {
                ExponentialHistogramDataPoint dataPoint = exponentialHistogram.getExponentialHistogram()
                    .getDataPoints(0);
                assertThat(dataPoint.getPositive().getBucketCountsList()).hasSize(56);
            });
    }

    @Test
    void longestMatchWinsByDefaultHistogramFlavorPerMeter() {
        Map<String, HistogramFlavor> histogramFlavorPerMeter = new HashMap<>();
        histogramFlavorPerMeter.put("http", EXPLICIT_BUCKET_HISTOGRAM);
        histogramFlavorPerMeter.put("http.server", BASE2_EXPONENTIAL_BUCKET_HISTOGRAM);

        assertThat(OtlpMeterRegistry.HistogramFlavorPerMeterLookup.DEFAULT.getHistogramFlavor(histogramFlavorPerMeter,
                createIdWithName("http.server.requests")))
            .isEqualTo(BASE2_EXPONENTIAL_BUCKET_HISTOGRAM);
    }

    @Test
    void longestMatchWinsByDefaultMaxBucketsPerMeter() {
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

    protected Metric writeToMetric(Meter meter) {
        return writeToMetrics(meter).get(0);
    }

    protected List<Metric> writeToMetrics(Meter meter) {
        OtlpMetricConverter otlpMetricConverter = new OtlpMetricConverter(clock, otlpConfig().step(),
                registry.getBaseTimeUnit(), otlpConfig().aggregationTemporality(), registry.config().namingConvention(),
                otlpConfig().publishMaxGaugeForHistograms());
        otlpMetricConverter.addMeter(meter);
        return otlpMetricConverter.getAllMetrics();

    }

    protected List<Metric> writeAllMeters() {
        OtlpMetricConverter otlpMetricConverter = new OtlpMetricConverter(clock, otlpConfig().step(),
                registry.getBaseTimeUnit(), otlpConfig().aggregationTemporality(), registry.config().namingConvention(),
                otlpConfig().publishMaxGaugeForHistograms());
        otlpMetricConverter.addMeters(registry.getMeters());
        return otlpMetricConverter.getAllMetrics();
    }

    protected void stepOverNStep(int numStepsToSkip) {
        clock.addSeconds(otlpConfig().step().toSeconds() * numStepsToSkip);
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

    private void assertMaxGaugeMetrics(List<Metric> metrics) {
        if (otlpConfig().publishMaxGaugeForHistograms()) {
            assertThat(metrics).filteredOn(Metric::hasGauge)
                .singleElement()
                .satisfies(gauge -> assertThat(gauge.getDataCase().getNumber())
                    .isEqualTo(Metric.DataCase.GAUGE.getNumber()));
        }
        else {
            assertThat(metrics).filteredOn(Metric::hasGauge).isEmpty();
        }
    }

    String encodeHexString(ByteString byteString) {
        return Hex.encodeHexString(byteString.toByteArray());
    }

}
