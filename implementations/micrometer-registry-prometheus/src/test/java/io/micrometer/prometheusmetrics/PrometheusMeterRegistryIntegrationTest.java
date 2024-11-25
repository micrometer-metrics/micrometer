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
package io.micrometer.prometheusmetrics;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.binder.jvm.JvmInfoMetrics;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.tracer.common.SpanContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.testcontainers.containers.BindMode.READ_ONLY;

/**
 * Integration tests for {@link PrometheusMeterRegistry}.
 *
 * @author Jonatan Ivanov
 */
@Testcontainers
@Tag("docker")
class PrometheusMeterRegistryIntegrationTest {

    @Container
    static GenericContainer<?> prometheus = new GenericContainer<>(
            DockerImageName.parse("prom/prometheus:" + getPrometheusImageVersion()))
        .withCommand("--config.file=/etc/prometheus/prometheus.yml")
        .withClasspathResourceMapping("prometheus.yml", "/etc/prometheus/prometheus.yml", READ_ONLY)
        .waitingFor(Wait.forLogMessage(".*Server is ready to receive web requests.*", 1))
        .withExposedPorts(9090)
        .withAccessToHost(true);

    private PrometheusMeterRegistry registry;

    @Nullable
    private HttpServer openMetricsServer;

    @Nullable
    private HttpServer prometheusTextServer;

    private static String getPrometheusImageVersion() {
        String version = System.getProperty("prometheus.version");
        if (version == null) {
            throw new IllegalStateException(
                    "System property 'prometheus.version' is not set. This should be set in the build configuration for running from the command line. If you are running PrometheusMeterRegistryIntegrationTest from an IDE, set the system property to the desired prom/prometheus image version.");
        }
        return version;
    }

    @BeforeEach
    void setUp() {
        org.testcontainers.Testcontainers.exposeHostPorts(12345, 12346);
        registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, new PrometheusRegistry(), Clock.SYSTEM,
                new TestSpanContext());
        System.out.println("Prometheus server is listening on http://localhost:" + prometheus.getFirstMappedPort());
    }

    @AfterEach
    void tearDown() {
        if (openMetricsServer != null) {
            openMetricsServer.stop(0);
            openMetricsServer = null;
        }
        if (prometheusTextServer != null) {
            prometheusTextServer.stop(0);
            prometheusTextServer = null;
        }
    }

    @Test
    void prometheusShouldScrapeOpenMetricsResult() throws IOException {
        this.openMetricsServer = startHttpServer(12345);
        verifyBuildInfo();
        recordMetrics();
        verifyOpenMetricsScrapeResult();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> verifyIfPrometheusHasAnyMetrics("test-app-om"));
        verifyPrometheusHasAllMetrics("test-app-om");
    }

    @Test
    void prometheusShouldScrapePrometheusTextResult() throws IOException {
        this.prometheusTextServer = startHttpServer(12346);
        verifyBuildInfo();
        recordMetrics();
        verifyPrometheusTextScrapeResult();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> verifyIfPrometheusHasAnyMetrics("test-app-pt"));
        verifyPrometheusHasAllMetrics("test-app-pt");
    }

    private void recordMetrics() {
        new JvmInfoMetrics().bindTo(registry);
        registry.counter("test.counter").increment();
        registry.gauge("test.gauge", prometheus.getFirstMappedPort());
        DistributionSummary.builder("test.ds")
            .publishPercentileHistogram()
            .serviceLevelObjectives(10, 100)
            .publishPercentiles(0.99)
            .register(registry)
            .record(42);
        Timer.builder("test.timer")
            .publishPercentileHistogram()
            .serviceLevelObjectives(Duration.ofMillis(10), Duration.ofMillis(100))
            .publishPercentiles(0.99)
            .register(registry)
            .record(200, MILLISECONDS);
        LongTaskTimer.builder("test.ltt").publishPercentileHistogram().register(registry).start();
        // @formatter:off
        FunctionTimer.builder(
            "test.ft",
                prometheus,
                GenericContainer::getStartupAttempts,
                ContainerState::getFirstMappedPort,
                SECONDS
            ).register(registry);
        // @formatter:on
        FunctionCounter.builder("test.fc", prometheus, ContainerState::getFirstMappedPort).register(registry);
        TimeGauge.builder("test.tg", () -> 42, MILLISECONDS).register(registry);

        List<Measurement> measurements = new ArrayList<>();
        for (int i = 0; i < Statistic.values().length; i++) {
            final int value = i;
            measurements.add(new Measurement(() -> value + 10, Statistic.values()[i]));
        }
        Meter.builder("test.custom", Type.OTHER, measurements).register(registry);
    }

    private void verifyBuildInfo() {
        // @formatter:off
        given()
            .port(prometheus.getFirstMappedPort())
        .when()
            .get("/api/v1/status/buildinfo")
        .then()
            .body("status", equalTo("success"))
            .body("data.version", startsWith("2."))
            .statusCode(200);
        // @formatter:on
    }

    private void verifyOpenMetricsScrapeResult() {
        // @formatter:off
        given()
            .port(12345)
        .when()
            .header("Accept", "application/openmetrics-text")
            .get("/metrics")
        .then()
            .statusCode(200)
            .header("Content-Type", "application/openmetrics-text; version=1.0.0; charset=utf-8")
            .body(endsWith("# EOF\n")) // indicates OpenMetrics body
            // exemplars should present
            .body(containsString("test_counter_total 1.0 # {span_id=\"321\",trace_id=\"123\"} 1.0 "))
            .body(containsString("test_ds_bucket{le=\"46.0\"} 1 # {span_id=\"321\",trace_id=\"123\"} 42.0"))
            .body(containsString("test_ds_count 1 # {span_id=\"321\",trace_id=\"123\"} 42.0"))
            .body(containsString("test_timer_seconds_bucket{le=\"0.20132659\"} 1 # {span_id=\"321\",trace_id=\"123\"} 0.2"))
            .body(containsString("test_timer_seconds_count 1 # {span_id=\"321\",trace_id=\"123\"} 0.2"));
        // @formatter:on
    }

    private void verifyPrometheusTextScrapeResult() {
        // @formatter:off
        given()
            .port(12346)
        .when()
            .header("Accept", "*/*")
            .get("/metrics")
        .then()
            .statusCode(200)
            .header("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
            .body(not(contains("# EOF")));
        // @formatter:on
    }

    private void verifyIfPrometheusHasAnyMetrics(String jobName) {
        // @formatter:off
        given()
            .port(prometheus.getFirstMappedPort())
        .when()
            .queryParam("match[]", "{job=\"" + jobName + "\"}")
            .get("/federate")
        .then()
            .statusCode(200)
            .body(containsString("test_counter_total"));
        // @formatter:on
    }

    private void verifyPrometheusHasAllMetrics(String jobName) {
        // @formatter:off
        given()
            .port(prometheus.getFirstMappedPort())
        .when()
            .queryParam("match[]", "{job=\"" + jobName + "\"}")
            .get("/federate")
        .then()
            .statusCode(200)
            .body(
                containsString("jvm_info"),
                containsString("test_counter_total"),
                containsString("test_gauge"),

                containsString("test_ds_bucket"),
                containsString("test_ds_count"),
                containsString("test_ds_sum"),
                containsString("test_ds_max"),

                containsString("test_timer_seconds_bucket"),
                containsString("test_timer_seconds_count"),
                containsString("test_timer_seconds_sum"),
                containsString("test_timer_seconds_max"),

                containsString("test_ltt_seconds_bucket"),
                containsString("test_ltt_seconds_gcount"),
                containsString("test_ltt_seconds_gsum"),
                containsString("test_ltt_seconds_max"),

                containsString("test_ft_seconds_count"),
                containsString("test_ft_seconds_sum"),
                containsString("test_fc_total"),
                containsString("test_tg_seconds"),

                containsString("test_custom_total"),
                containsString("test_custom_active_count"),
                containsString("test_custom_duration_sum"),
                containsString("test_custom_max"),
                containsString("test_custom_sum_total"),
                containsString("test_custom_value")
            );
        // @formatter:on
    }

    private HttpServer startHttpServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/metrics", httpExchange -> {
            String acceptHeader = httpExchange.getRequestHeaders().getFirst("Accept");
            String contentType;
            if (acceptHeader.startsWith("application/openmetrics-text")) {
                contentType = "application/openmetrics-text; version=1.0.0; charset=utf-8";
            }
            else {
                contentType = "text/plain; version=0.0.4; charset=utf-8";
            }
            String response = registry.scrape(acceptHeader);

            httpExchange.getResponseHeaders().add("Content-Type", contentType);
            httpExchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream outputStream = httpExchange.getResponseBody()) {
                outputStream.write(response.getBytes());
            }
        });
        new Thread(server::start).start();

        return server;
    }

    static class TestSpanContext implements SpanContext {

        @Override
        public String getCurrentTraceId() {
            return "123";
        }

        @Override
        public String getCurrentSpanId() {
            return "321";
        }

        @Override
        public boolean isCurrentSpanSampled() {
            return true;
        }

        @Override
        public void markCurrentSpanAsExemplar() {
            // noop
        }

    }

}
