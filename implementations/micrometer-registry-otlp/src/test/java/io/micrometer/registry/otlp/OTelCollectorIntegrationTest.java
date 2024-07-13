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
import io.restassured.response.Response;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.testcontainers.containers.BindMode.READ_ONLY;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

/**
 * Integration tests for {@link OtlpMeterRegistry} and the OTel collector
 *
 * @author Jonatan Ivanov
 */
@Testcontainers
@Tag("docker")
class OTelCollectorIntegrationTest {

    private static final String OPENMETRICS_TEXT = "application/openmetrics-text; version=1.0.0; charset=utf-8";

    private static final String CONFIG_FILE_NAME = "collector-config.yml";

    private static final DockerImageName COLLECTOR_IMAGE = DockerImageName
        .parse("otel/opentelemetry-collector-contrib:" + getCollectorImageVersion());

    @Container
    private final GenericContainer<?> container = new GenericContainer(COLLECTOR_IMAGE)
        .withCommand("--config=/etc/" + CONFIG_FILE_NAME)
        .withClasspathResourceMapping(CONFIG_FILE_NAME, "/etc/" + CONFIG_FILE_NAME, READ_ONLY)
        .withExposedPorts(4318, 9090) // HTTP receiver, Prometheus exporter
        .waitingFor(Wait.forLogMessage(".*Everything is ready.*", 1))
        .waitingFor(Wait.forListeningPorts(4318));

    private static String getCollectorImageVersion() {
        String version = System.getProperty("otel-collector-image.version");
        if (version == null) {
            throw new IllegalStateException(
                    "System property 'otel-collector-image.version' is not set. This should be set in the build configuration for running from the command line. If you are running OTelCollectorIntegrationTest from an IDE, set the system property to the desired collector image version.");
        }
        return version;
    }

    @Test
    void collectorShouldExportMetrics() throws Exception {
        MeterRegistry registry = createOtlpMeterRegistryForContainer(container);
        Counter.builder("test.counter").register(registry).increment(42);
        Gauge.builder("test.gauge", () -> 12).register(registry);
        Timer.builder("test.timer").register(registry).record(Duration.ofMillis(123));
        DistributionSummary.builder("test.ds").register(registry).record(24);

        // @formatter:off
        await().atMost(Duration.ofSeconds(5))
            .pollDelay(Duration.ofMillis(100))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> whenPrometheusScraped().then()
                    .statusCode(200)
                    .contentType(OPENMETRICS_TEXT)
                    .body(endsWith("# EOF\n"), not(startsWith("# EOF\n")))
            );

        // tags can vary depending on where you run your tests:
        //  - IDE: no telemetry_sdk_version tag
        //  - Gradle: telemetry_sdk_version has the version number
        whenPrometheusScraped().then().body(
            containsString("{job=\"test\",service_name=\"test\",telemetry_sdk_language=\"java\",telemetry_sdk_name=\"io.micrometer\""),

            containsString("# HELP test_counter \n"),
            containsString("# TYPE test_counter counter\n"),
            matchesPattern("(?s)^.*test_counter_total\\{.+} 42\\.0\\n.*$"),

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
            matchesPattern("(?s)^.*test_timer_milliseconds_bucket\\{.+,le=\"\\+Inf\"} 1\\n.*$"),

            containsString("# HELP test_ds \n"),
            containsString("# TYPE test_ds histogram\n"),
            matchesPattern("(?s)^.*test_ds_count\\{.+} 1\\n.*$"),
            matchesPattern("(?s)^.*test_ds_sum\\{.+} 24\\.0\\n.*$"),
            matchesPattern("(?s)^.*test_ds_bucket\\{.+,le=\"\\+Inf\"} 1\\n.*$")
        );
        // @formatter:on
    }

    private OtlpMeterRegistry createOtlpMeterRegistryForContainer(GenericContainer<?> container) throws Exception {
        return withEnvironmentVariables("OTEL_SERVICE_NAME", "test")
            .execute(() -> new OtlpMeterRegistry(createOtlpConfigForContainer(container), Clock.SYSTEM));
    }

    private OtlpConfig createOtlpConfigForContainer(GenericContainer<?> container) {
        return new OtlpConfig() {
            @Override
            public String url() {
                return String.format("http://%s:%d/v1/metrics", container.getHost(), container.getMappedPort(4318));
            }

            @Override
            public Duration step() {
                return Duration.ofSeconds(1);
            }

            @Override
            public String get(String key) {
                return null;
            }
        };
    }

    private Response whenPrometheusScraped() {
        // @formatter:off
        return given()
            .port(container.getMappedPort(9090))
            .accept(OPENMETRICS_TEXT)
            .when()
            .get("/metrics");
        // @formatter:on
    }

}
