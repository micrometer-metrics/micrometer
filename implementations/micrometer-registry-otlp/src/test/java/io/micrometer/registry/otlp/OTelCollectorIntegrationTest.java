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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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

    // TODO: The OTel Prometheus exporter does not support openmetrics-text 1.0.0 yet
    // see: https://github.com/open-telemetry/opentelemetry-collector-contrib/issues/18913
    private static final String OPENMETRICS_001 = "application/openmetrics-text; version=0.0.1; charset=utf-8";

    private static final String CONFIG_FILE_NAME = "collector-config.yml";

    @Container
    private final GenericContainer<?> container = new GenericContainer("otel/opentelemetry-collector-contrib")
        .withCommand("--config=/etc/" + CONFIG_FILE_NAME)
        .withClasspathResourceMapping(CONFIG_FILE_NAME, "/etc/" + CONFIG_FILE_NAME, READ_ONLY)
        .withExposedPorts(4318, 9090); // HTTP receiver, Prometheus exporter

    @Test
    void collectorShouldExportMetrics() throws Exception {
        MeterRegistry registry = createOtlpMeterRegistryForContainer(container);
        Counter.builder("test.counter").register(registry).increment(42);
        Gauge.builder("test.gauge", () -> 12).register(registry);
        Timer.builder("test.timer").register(registry).record(Duration.ofMillis(123));
        DistributionSummary.builder("test.distributionsummary").register(registry).record(24);

        // @formatter:off
        await().atMost(Duration.ofSeconds(5))
            .pollDelay(Duration.ofMillis(100))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> whenPrometheusScraped().then()
                    .statusCode(200)
                    .contentType(OPENMETRICS_001)
                    .body(endsWith("# EOF\n"))
            );

        // tags can vary depending on where you run your tests:
        //  - IDE: no telemetry_sdk_version tag
        //  - Gradle: telemetry_sdk_version has the version number
        whenPrometheusScraped().then().body(
            containsString("{job=\"test\",service_name=\"test\",telemetry_sdk_language=\"java\",telemetry_sdk_name=\"io.micrometer\""),

            matchesPattern("(?s)^.*test_counter\\{.+} 42\\.0\\n.*$"),
            matchesPattern("(?s)^.*test_gauge\\{.+} 12\\.0\\n.*$"),

            matchesPattern("(?s)^.*test_timer_count\\{.+} 1\\n.*$"),
            // TODO: this should be 123ms (0.123) not 123s (123)
            // see: https://github.com/open-telemetry/opentelemetry-collector-contrib/issues/18903
            matchesPattern("(?s)^.*test_timer_sum\\{.+} 123\\.0\\n.*$"),
            matchesPattern("(?s)^.*test_timer_bucket\\{.+,le=\"\\+Inf\"} 1\\n.*$"),

            matchesPattern("(?s)^.*test_distributionsummary_count\\{.+} 1\\n.*$"),
            matchesPattern("(?s)^.*test_distributionsummary_sum\\{.+} 24\\.0\\n.*$"),
            matchesPattern("(?s)^.*test_distributionsummary_bucket\\{.+,le=\"\\+Inf\"} 1\\n.*$")
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
            .accept(OPENMETRICS_001)
            .when()
            .get("/metrics");
        // @formatter:on
    }

}
