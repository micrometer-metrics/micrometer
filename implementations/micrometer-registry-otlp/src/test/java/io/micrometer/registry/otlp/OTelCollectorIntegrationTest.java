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
import io.prometheus.metrics.expositionformats.generated.Metrics;
import io.restassured.response.Response;
import org.hamcrest.Matcher;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.micrometer.registry.otlp.CompressionMode.GZIP;
import static io.micrometer.registry.otlp.CompressionMode.NONE;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.testcontainers.containers.BindMode.READ_ONLY;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

/**
 * Integration tests for {@link OtlpMeterRegistry} and the OTel collector
 *
 * @author Jonatan Ivanov
 */
@Tag("docker")
@Testcontainers
class OTelCollectorIntegrationTest {

    private static final String OPENMETRICS_TEXT = "application/openmetrics-text; version=1.0.0; charset=utf-8";

    private static final String PROMETHEUS_PROTOBUF = "application/vnd.google.protobuf; proto=io.prometheus.client.MetricFamily; encoding=delimited; escaping=underscores";

    private static final String CONFIG_FILE_NAME = "collector-config.yml";

    private static final DockerImageName COLLECTOR_IMAGE = getOtelCollectorImage();

    @Container
    @SuppressWarnings("rawtypes")
    private final GenericContainer<?> container = new GenericContainer(COLLECTOR_IMAGE)
        .withCommand("--config=/etc/" + CONFIG_FILE_NAME)
        .withClasspathResourceMapping(CONFIG_FILE_NAME, "/etc/" + CONFIG_FILE_NAME, READ_ONLY)
        .withExposedPorts(4318, 9090) // HTTP receiver, Prometheus exporter
        .waitingFor(Wait.forLogMessage(".*Everything is ready.*", 1))
        .waitingFor(Wait.forHttp("/metrics").forPort(9090).forStatusCode(200));

    // .waitingFor(Wait.forListeningPorts(4318)) does not work since Testcontainers wants
    // to run "/bin/sh" which is not available in this image

    private final TestExemplarContextProvider contextProvider = new TestExemplarContextProvider();

    private static DockerImageName getOtelCollectorImage() {
        String imageName = System.getProperty("otel-collector-image.name");
        if (imageName == null) {
            throw new IllegalStateException(
                    "System property 'otel-collector-image.name' is not set. This should be set in the build configuration for running from the command line. If you are running OTelCollectorIntegrationTest from an IDE, set the system property to the desired collector image name.");
        }
        return DockerImageName.parse(imageName);
    }

    @Test
    void collectorShouldExportMetrics() throws Exception {
        MeterRegistry registry = createOtlpMeterRegistryForContainer(container);
        record("66fd7359621b3043e232148ef0c4c566", "e232148ef0c4c566", () -> {
            Counter.builder("test.counter").register(registry).increment(42);
            Gauge.builder("test.gauge", () -> 12).register(registry);
            Timer.builder("test.timer").register(registry).record(Duration.ofMillis(123));
            DistributionSummary.builder("test.ds").register(registry).record(24);
        });

        // @formatter:off
        Response response = await()
            .atMost(Duration.ofSeconds(10))
            .pollDelay(Duration.ofMillis(100))
            .pollInterval(Duration.ofMillis(100))
            .until(this::whenPrometheusScrapedWithOpenMetrics, this::doesPrometheusResponseContainValidOpenMetricsData);

        // tags can vary depending on where you run your tests:
        //  - IDE: no telemetry_sdk_version tag
        //  - Gradle: telemetry_sdk_version has the version number
        response.then().body(
            containsString("{job=\"test\",otel_scope_name=\"\",otel_scope_schema_url=\"\",otel_scope_version=\"\",service_name=\"test\",telemetry_sdk_language=\"java\",telemetry_sdk_name=\"io.micrometer\""),

            containsString("# HELP test_counter \n"),
            containsString("# TYPE test_counter counter\n"),
            matchesPattern("(?s)^.*test_counter_total\\{.+} 42\\.0 # \\{.*trace_id=\"66fd7359621b3043e232148ef0c4c566\".*} 42\\.0 1\\.\\d+e\\+09\\n.*$"),
            matchesPattern("(?s)^.*test_counter_total\\{.+} 42\\.0 # \\{.*span_id=\"e232148ef0c4c566\".*} 42\\.0 1\\.\\d+e\\+09\\n.*$"),

            containsString("# HELP test_gauge \n"),
            containsString("# TYPE test_gauge gauge\n"),
            matchesPattern("(?s)^.*test_gauge\\{.+} 12\\.0\\n.*$"),

            containsString("# HELP test_timer_milliseconds \n"),
            containsString("# TYPE test_timer_milliseconds histogram\n"),
            matchesPattern("(?s)^.*test_timer_milliseconds_count\\{.+} 1\\n.*$"),
            // Earlier this was 123s (123), should have been 123ms (0.123)
            // see: https://github.com/open-telemetry/opentelemetry-collector-contrib/issues/18903
            // it seems units are still not converted but at least the unit is in the name now (breaking change)
            // see: https://github.com/open-telemetry/opentelemetry-collector-contrib/pull/20519
            matchesPattern("(?s)^.*test_timer_milliseconds_sum\\{.+} 123\\.0\\n.*$"),
            matchesPattern("(?s)^.*test_timer_milliseconds_bucket\\{.+,le=\"\\+Inf\"} 1 # \\{.*trace_id=\"66fd7359621b3043e232148ef0c4c566\".*} 123\\.0 1\\.\\d+e\\+09\\n.*$"),
            matchesPattern("(?s)^.*test_timer_milliseconds_bucket\\{.+,le=\"\\+Inf\"} 1 # \\{.*span_id=\"e232148ef0c4c566\".*} 123\\.0 1\\.\\d+e\\+09\\n.*$"),

            containsString("# HELP test_timer_max_milliseconds \n"),
            containsString("# TYPE test_timer_max_milliseconds gauge\n"),
            matchesPattern("(?s)^.*test_timer_max_milliseconds\\{.+} 123\\.0\n.*$"),

            containsString("# HELP test_ds \n"),
            containsString("# TYPE test_ds histogram\n"),
            matchesPattern("(?s)^.*test_ds_count\\{.+} 1\\n.*$"),
            matchesPattern("(?s)^.*test_ds_sum\\{.+} 24\\.0\\n.*$"),
            matchesPattern("(?s)^.*test_ds_max\\{.+} 24\\.0\\n.*$"),
            matchesPattern("(?s)^.*test_ds_bucket\\{.+,le=\"\\+Inf\"} 1 # \\{.*trace_id=\"66fd7359621b3043e232148ef0c4c566\".*} 24\\.0 1\\.\\d+e\\+09\\n.*$"),
            matchesPattern("(?s)^.*test_ds_bucket\\{.+,le=\"\\+Inf\"} 1 # \\{.*span_id=\"e232148ef0c4c566\".*} 24\\.0 1\\.\\d+e\\+09\\n.*$")
        );
        // @formatter:on
    }

    @Test
    void collectorShouldExportExemplarsOnHistograms() throws Exception {
        MeterRegistry registry = createOtlpMeterRegistryForContainer(container);
        Timer timer = Timer.builder("test.timer").publishPercentileHistogram().register(registry);
        DistributionSummary ds = DistributionSummary.builder("test.ds").publishPercentileHistogram().register(registry);

        record("66fd7359621b3043e232148ef0c40001", "e232148ef0c40001", () -> timer.record(Duration.ofMillis(1)));
        record("66fd7359621b3043e232148ef0c40002", "e232148ef0c40002", () -> timer.record(Duration.ofMillis(123)));
        record("66fd7359621b3043e232148ef0c40003", "e232148ef0c40003", () -> timer.record(Duration.ofSeconds(30)));
        record("66fd7359621b3043e232148ef0c40004", "e232148ef0c40004", () -> timer.record(Duration.ofSeconds(42)));

        record("66fd7359621b3043e232148ef0c40005", "e232148ef0c40005", () -> ds.record(1));
        record("66fd7359621b3043e232148ef0c40006", "e232148ef0c40006", () -> ds.record(123));
        record("66fd7359621b3043e232148ef0c40007", "e232148ef0c40007", () -> ds.record(4.2E18));
        record("66fd7359621b3043e232148ef0c40008", "e232148ef0c40008", () -> ds.record(5.0E18));

        // @formatter:off
        Response response = await()
            .atMost(Duration.ofSeconds(10))
            .pollDelay(Duration.ofMillis(100))
            .pollInterval(Duration.ofMillis(100))
            .until(this::whenPrometheusScrapedWithOpenMetrics, this::doesPrometheusResponseContainValidOpenMetricsData);

        // tags can vary depending on where you run your tests:
        //  - IDE: no telemetry_sdk_version tag
        //  - Gradle: telemetry_sdk_version has the version number
        response.then().body(
            containsString("{job=\"test\",otel_scope_name=\"\",otel_scope_schema_url=\"\",otel_scope_version=\"\",service_name=\"test\",telemetry_sdk_language=\"java\",telemetry_sdk_name=\"io.micrometer\""),

            containsString("# HELP test_timer_milliseconds \n"),
            containsString("# TYPE test_timer_milliseconds histogram\n"),
            matchesPattern("(?s)^.*test_timer_milliseconds_count\\{.+} 4\\n.*$"),
            matchesPattern("(?s)^.*test_timer_milliseconds_sum\\{.+} 72124\\.0\\n.*$"),

            matchesPattern("(?s)^.*test_timer_milliseconds_bucket\\{.+,le=\"1\\.0\"} 1 # \\{.*trace_id=\"66fd7359621b3043e232148ef0c40001\".*} 1\\.0 1\\.\\d+e\\+09\\n.*$"),
            matchesPattern("(?s)^.*test_timer_milliseconds_bucket\\{.+,le=\"1\\.0\"} 1 # \\{.*span_id=\"e232148ef0c40001\".*} 1\\.0 1\\.\\d+e\\+09\\n.*$"),

            matchesPattern("(?s)^.*test_timer_milliseconds_bucket\\{.+,le=\"134\\.217727\"} 2 # \\{.*trace_id=\"66fd7359621b3043e232148ef0c40002\".*} 123\\.0 1\\.\\d+e\\+09\\n.*$"),
            matchesPattern("(?s)^.*test_timer_milliseconds_bucket\\{.+,le=\"134\\.217727\"} 2 # \\{.*span_id=\"e232148ef0c40002\".*} 123\\.0 1\\.\\d+e\\+09\\n.*$"),

            matchesPattern("(?s)^.*test_timer_milliseconds_bucket\\{.+,le=\"30000\\.0\"} 3 # \\{.*trace_id=\"66fd7359621b3043e232148ef0c40003\".*} 30000\\.0 1\\.\\d+e\\+09\\n.*$"),
            matchesPattern("(?s)^.*test_timer_milliseconds_bucket\\{.+,le=\"30000\\.0\"} 3 # \\{.*span_id=\"e232148ef0c40003\".*} 30000\\.0 1\\.\\d+e\\+09\\n.*$"),

            matchesPattern("(?s)^.*test_timer_milliseconds_bucket\\{.+,le=\"\\+Inf\"} 4 # \\{.*trace_id=\"66fd7359621b3043e232148ef0c40004\".*} 42000\\.0 1\\.\\d+e\\+09\\n.*$"),
            matchesPattern("(?s)^.*test_timer_milliseconds_bucket\\{.+,le=\"\\+Inf\"} 4 # \\{.*span_id=\"e232148ef0c40004\".*} 42000\\.0 1\\.\\d+e\\+09\\n.*$"),

            containsString("# HELP test_ds \n"),
            containsString("# TYPE test_ds histogram\n"),
            matchesPattern("(?s)^.*test_ds_count\\{.+} 4\\n.*$"),
            matchesPattern("(?s)^.*test_ds_sum\\{.+} 9\\.2e\\+18\\n.*$"),

            matchesPattern("(?s)^.*test_ds_bucket\\{.+,le=\"1\\.0\"} 1 # \\{.*trace_id=\"66fd7359621b3043e232148ef0c40005\".*} 1\\.0 1\\.\\d+e\\+09\\n.*$"),
            matchesPattern("(?s)^.*test_ds_bucket\\{.+,le=\"1\\.0\"} 1 # \\{.*span_id=\"e232148ef0c40005\".*} 1\\.0 1\\.\\d+e\\+09\\n.*$"),

            matchesPattern("(?s)^.*test_ds_bucket\\{.+,le=\"127\\.0\"} 2 # \\{.*trace_id=\"66fd7359621b3043e232148ef0c40006\".*} 123\\.0 1\\.\\d+e\\+09\\n.*$"),
            matchesPattern("(?s)^.*test_ds_bucket\\{.+,le=\"127\\.0\"} 2 # \\{.*span_id=\"e232148ef0c40006\".*} 123\\.0 1\\.\\d+e\\+09\\n.*$"),

            matchesPattern("(?s)^.*test_ds_bucket\\{.+,le=\"4\\.2273788502251054e\\+18\"} 3 # \\{.*trace_id=\"66fd7359621b3043e232148ef0c40007\".*} 4\\.2e\\+18 1\\.\\d+e\\+09\\n.*$"),
            matchesPattern("(?s)^.*test_ds_bucket\\{.+,le=\"4\\.2273788502251054e\\+18\"} 3 # \\{.*span_id=\"e232148ef0c40007\".*} 4\\.2e\\+18 1\\.\\d+e\\+09\\n.*$"),

            matchesPattern("(?s)^.*test_ds_bucket\\{.+,le=\"\\+Inf\"} 4 # \\{.*trace_id=\"66fd7359621b3043e232148ef0c40008\".*} 5e\\+18 1\\.\\d+e\\+09\\n.*$"),
            matchesPattern("(?s)^.*test_ds_bucket\\{.+,le=\"\\+Inf\"} 4 # \\{.*span_id=\"e232148ef0c40008\".*} 5e\\+18 1\\.\\d+e\\+09\\n.*$")
        );
        // @formatter:on
    }

    @Test
    void collectorShouldExportExponentialHistograms() throws Exception {
        // Shows that the collector received the exemplars:
        // container.followOutput(frame -> System.out.println(frame.getUtf8String()));
        MeterRegistry registry = createOtlpMeterRegistryForContainerWithExponentialHistogram(container);
        Timer timer = Timer.builder("test.timer").publishPercentileHistogram().register(registry);
        DistributionSummary ds = DistributionSummary.builder("test.ds").publishPercentileHistogram().register(registry);

        record("66fd7359621b3043e232148ef0c40001", "e232148ef0c40001", () -> timer.record(Duration.ofMillis(1)));
        record("66fd7359621b3043e232148ef0c40002", "e232148ef0c40002", () -> timer.record(Duration.ofMillis(123)));

        record("66fd7359621b3043e232148ef0c40005", "e232148ef0c40005", () -> ds.record(1));
        record("66fd7359621b3043e232148ef0c40006", "e232148ef0c40006", () -> ds.record(123));

        // @formatter:off
        Response response = await()
            .atMost(Duration.ofSeconds(10))
            .pollDelay(Duration.ofMillis(100))
            .pollInterval(Duration.ofMillis(100))
            .until(this::whenPrometheusScrapedWithProtobuf, this::doesPrometheusResponseContainValidProtobufData);

        List<Metrics.MetricFamily> families = parse(response);
        assertThat(families.size()).isEqualTo(2);
        assertThat(families).allSatisfy(family -> {
            assertThat(family.getMetricCount()).isEqualTo(1);
            assertThat(family.getMetric(0).getHistogram().getPositiveDeltaCount()).isPositive();
            // TODO: Check Exemplars after support is added to the collector,
            //  see: https://github.com/open-telemetry/opentelemetry-collector-contrib/issues/47159
            //  assertThat(family.getMetric(0).getHistogram().getExemplarsCount()).isPositive();
        });
        // @formatter:on
    }

    private List<io.prometheus.metrics.expositionformats.generated.Metrics.MetricFamily> parse(Response response)
            throws IOException {
        List<Metrics.MetricFamily> families = new ArrayList<>();
        try (InputStream is = response.body().asInputStream()) {
            Metrics.MetricFamily family;
            while ((family = Metrics.MetricFamily.parseDelimitedFrom(is)) != null) {
                families.add(family);
            }
        }

        return families;
    }

    @Test
    void collectorShouldExportMetricsWithGzipCompression() throws Exception {
        MeterRegistry registry = createOtlpMeterRegistryForContainerWithGzipCompression(container);

        record("66fd7359621b3043e232148ef0c4c566", "e232148ef0c4c566", () -> {
            Counter.builder("test.counter.gzip").register(registry).increment(42);
            Gauge.builder("test.gauge.gzip", () -> 12).register(registry);
            Timer.builder("test.timer.gzip").register(registry).record(Duration.ofMillis(123));
            DistributionSummary.builder("test.ds.gzip").register(registry).record(24);
        });

        // @formatter:off
        Response response = await()
            .atMost(Duration.ofSeconds(10))
            .pollDelay(Duration.ofMillis(100))
            .pollInterval(Duration.ofMillis(100))
            .until(this::whenPrometheusScrapedWithOpenMetrics, this::doesPrometheusResponseContainValidOpenMetricsData);

        // tags can vary depending on where you run your tests:
        //  - IDE: no telemetry_sdk_version tag
        //  - Gradle: telemetry_sdk_version has the version number
        response.then().body(
            containsString("{job=\"test\",otel_scope_name=\"\",otel_scope_schema_url=\"\",otel_scope_version=\"\",service_name=\"test\",telemetry_sdk_language=\"java\",telemetry_sdk_name=\"io.micrometer\""),

            containsString("# HELP test_counter_gzip \n"),
            containsString("# TYPE test_counter_gzip counter\n"),
            matchesPattern("(?s)^.*test_counter_gzip_total\\{.+} 42\\.0 # \\{.*trace_id=\"66fd7359621b3043e232148ef0c4c566\".*} 42\\.0 1\\.\\d+e\\+09\\n.*$"),
            matchesPattern("(?s)^.*test_counter_gzip_total\\{.+} 42\\.0 # \\{.*span_id=\"e232148ef0c4c566\".*} 42\\.0 1\\.\\d+e\\+09\\n.*$"),

            containsString("# HELP test_gauge_gzip \n"),
            containsString("# TYPE test_gauge_gzip gauge\n"),
            matchesPattern("(?s)^.*test_gauge_gzip\\{.+} 12\\.0\\n.*$"),

            containsString("# HELP test_timer_gzip_milliseconds \n"),
            containsString("# TYPE test_timer_gzip_milliseconds histogram\n"),
            matchesPattern("(?s)^.*test_timer_gzip_milliseconds_count\\{.+} 1\\n.*$"),
            matchesPattern("(?s)^.*test_timer_gzip_milliseconds_sum\\{.+} 123\\.0\\n.*$"),
            matchesPattern("(?s)^.*test_timer_gzip_milliseconds_bucket\\{.+,le=\"\\+Inf\"} 1 # \\{.*trace_id=\"66fd7359621b3043e232148ef0c4c566\".*} 123\\.0 1\\.\\d+e\\+09\\n.*$"),
            matchesPattern("(?s)^.*test_timer_gzip_milliseconds_bucket\\{.+,le=\"\\+Inf\"} 1 # \\{.*span_id=\"e232148ef0c4c566\".*} 123\\.0 1\\.\\d+e\\+09\\n.*$"),

            containsString("# HELP test_ds_gzip \n"),
            containsString("# TYPE test_ds_gzip histogram\n"),
            matchesPattern("(?s)^.*test_ds_gzip_count\\{.+} 1\\n.*$"),
            matchesPattern("(?s)^.*test_ds_gzip_sum\\{.+} 24\\.0\\n.*$"),
            matchesPattern("(?s)^.*test_ds_gzip_bucket\\{.+,le=\"\\+Inf\"} 1 # \\{.*trace_id=\"66fd7359621b3043e232148ef0c4c566\".*} 24\\.0 1\\.\\d+e\\+09\\n.*$"),
            matchesPattern("(?s)^.*test_ds_gzip_bucket\\{.+,le=\"\\+Inf\"} 1 # \\{.*span_id=\"e232148ef0c4c566\".*} 24\\.0 1\\.\\d+e\\+09\\n.*$")
        );
        // @formatter:on
    }

    @Test
    void collectorShouldNotExportMaxMetricsWhenPublishHistogramMaxIsFalse() throws Exception {
        MeterRegistry registry = createOtlpMeterRegistryForContainerWithoutMaxGauge(container);
        Timer.builder("test.timer.nomax").register(registry).record(Duration.ofMillis(123));
        DistributionSummary.builder("test.ds.nomax").register(registry).record(24);

        // @formatter:off
        Response response = await()
            .atMost(Duration.ofSeconds(10))
            .pollDelay(Duration.ofMillis(100))
            .pollInterval(Duration.ofMillis(100))
            .until(this::whenPrometheusScrapedWithOpenMetrics, this::doesPrometheusResponseContainValidOpenMetricsData);

        response.then().body(
            // Verify timer histogram is exported
            containsString("# HELP test_timer_nomax_milliseconds \n"),
            containsString("# TYPE test_timer_nomax_milliseconds histogram\n"),
            matchesPattern("(?s)^.*test_timer_nomax_milliseconds_count\\{.+} 1\\n.*$"),
            matchesPattern("(?s)^.*test_timer_nomax_milliseconds_sum\\{.+} 123\\.0\\n.*$"),
            matchesPattern("(?s)^.*test_timer_nomax_milliseconds_bucket\\{.+,le=\"\\+Inf\"} 1\\n.*$"),
            // Verify distribution summary histogram is exported
            containsString("# HELP test_ds_nomax \n"),
            containsString("# TYPE test_ds_nomax histogram\n"),
            matchesPattern("(?s)^.*test_ds_nomax_count\\{.+} 1\\n.*$"),
            matchesPattern("(?s)^.*test_ds_nomax_sum\\{.+} 24\\.0\\n.*$"),
            matchesPattern("(?s)^.*test_ds_nomax_bucket\\{.+,le=\"\\+Inf\"} 1\\n.*$"),
            // Verify .max gauges are NOT exported
            not(containsString("# HELP test_timer_nomax_max_milliseconds \n")),
            not(containsString("# TYPE test_timer_nomax_max_milliseconds gauge\n")),
            not(matchesPattern("(?s)^.*test_timer_nomax_max_milliseconds\\{.+} 123\\.0\n.*$"))
        );
        // @formatter:on
    }

    private OtlpMeterRegistry createOtlpMeterRegistryForContainer(GenericContainer<?> container) throws Exception {
        return withEnvironmentVariables("OTEL_SERVICE_NAME", "test")
            .execute(() -> OtlpMeterRegistry.builder(createOtlpConfigForContainer(container))
                .exemplarContextProvider(contextProvider)
                .build());
    }

    private OtlpMeterRegistry createOtlpMeterRegistryForContainerWithGzipCompression(GenericContainer<?> container)
            throws Exception {
        return withEnvironmentVariables("OTEL_SERVICE_NAME", "test")
            .execute(() -> OtlpMeterRegistry.builder(createOtlpConfigForContainer(container, GZIP))
                .exemplarContextProvider(contextProvider)
                .build());
    }

    private OtlpMeterRegistry createOtlpMeterRegistryForContainerWithoutMaxGauge(GenericContainer<?> container)
            throws Exception {
        return withEnvironmentVariables("OTEL_SERVICE_NAME", "test").execute(() -> new OtlpMeterRegistry(
                createOtlpConfigForContainer(container, NONE, false, HistogramFlavor.EXPLICIT_BUCKET_HISTOGRAM),
                Clock.SYSTEM));
    }

    private OtlpMeterRegistry createOtlpMeterRegistryForContainerWithExponentialHistogram(GenericContainer<?> container)
            throws Exception {
        return withEnvironmentVariables("OTEL_SERVICE_NAME", "test").execute(() -> OtlpMeterRegistry
            .builder(createOtlpConfigForContainer(container, NONE, false,
                    HistogramFlavor.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM))
            .exemplarContextProvider(contextProvider)
            .build());
    }

    private OtlpConfig createOtlpConfigForContainer(GenericContainer<?> container) {
        return createOtlpConfigForContainer(container, NONE);
    }

    private OtlpConfig createOtlpConfigForContainer(GenericContainer<?> container, CompressionMode compressionMode) {
        return createOtlpConfigForContainer(container, compressionMode, true,
                HistogramFlavor.EXPLICIT_BUCKET_HISTOGRAM);
    }

    private OtlpConfig createOtlpConfigForContainer(GenericContainer<?> container, CompressionMode compressionMode,
            boolean publishMaxGaugeForHistograms, HistogramFlavor histogramFlavor) {
        return new OtlpConfig() {
            @Override
            public @NonNull String url() {
                return String.format("http://%s:%d/v1/metrics", container.getHost(), container.getMappedPort(4318));
            }

            @Override
            public @NonNull Duration step() {
                return Duration.ofSeconds(5);
            }

            @Override
            public @NonNull CompressionMode compressionMode() {
                return compressionMode;
            }

            @Override
            public boolean publishMaxGaugeForHistograms() {
                return publishMaxGaugeForHistograms;
            }

            @Override
            public @NonNull HistogramFlavor histogramFlavor() {
                return histogramFlavor;
            }

            @Override
            public @NonNull TimeUnit baseTimeUnit() {
                return histogramFlavor == HistogramFlavor.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM ? TimeUnit.SECONDS
                        : TimeUnit.MILLISECONDS;
            }

            @Override
            public @Nullable String get(@NonNull String key) {
                return null;
            }
        };
    }

    private Response whenPrometheusScrapedWithOpenMetrics() {
        return whenPrometheusScrapedWith(OPENMETRICS_TEXT);
    }

    private Response whenPrometheusScrapedWithProtobuf() {
        return whenPrometheusScrapedWith(PROMETHEUS_PROTOBUF);
    }

    private Response whenPrometheusScrapedWith(String contentType) {
        // @formatter:off
        return given()
            .port(container.getMappedPort(9090))
            .accept(contentType)
            .when()
            .get("/metrics");
        // @formatter:on
    }

    private boolean doesPrometheusResponseContainValidOpenMetricsData(Response response) {
        return doesPrometheusResponseContainValidData(response, OPENMETRICS_TEXT,
                allOf(endsWith("# EOF\n"), not(startsWith("# EOF\n"))));
    }

    private boolean doesPrometheusResponseContainValidProtobufData(Response response) {
        return doesPrometheusResponseContainValidData(response, PROMETHEUS_PROTOBUF, containsString("io.micrometer"));
    }

    private boolean doesPrometheusResponseContainValidData(Response response, String contentType,
            Matcher<String> bodyMatcher) {
        try {
            response.then().statusCode(200).contentType(contentType).body(bodyMatcher);
            return true;
        }
        catch (AssertionError ignored) {
            return false;
        }
    }

    void record(String traceId, String spanId, Runnable runnable) {
        contextProvider.setExemplar(traceId, spanId);
        runnable.run();
        contextProvider.reset();
    }

    static class TestExemplarContextProvider implements ExemplarContextProvider {

        private @Nullable OtlpExemplarContext context;

        @Override
        public @Nullable OtlpExemplarContext getExemplarContext() {
            return context;
        }

        void setExemplar(String traceId, String spanId) {
            context = new OtlpExemplarContext(traceId, spanId);
        }

        void reset() {
            context = null;
        }

    }

}
