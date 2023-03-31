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

import java.time.Duration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesPattern;

/**
 * Integration tests for {@link OtlpMeterRegistry} (delta) and the OTel collector
 *
 * @author Jonatan Ivanov
 */
@Tag("docker")
class OTelCollectorDeltaIntegrationTest extends OTelCollectorIntegrationTest {

    @Override
    AggregationTemporality getAggregationTemporality() {
        return AggregationTemporality.DELTA;
    }

    // TODO: This should be deleted; it's a copy from the abstract test class but it seems
    // histogram is not working with the collector so Timer and DistributionSummary are
    // missing.
    @Test
    @Override
    void collectorShouldExportMetrics() throws Exception {
        MeterRegistry registry = createOtlpMeterRegistryForContainer(container);
        Counter.builder("test.counter").register(registry).increment(42);
        Gauge.builder("test.gauge", () -> 12).register(registry);
        Timer.builder("test.timer").register(registry).record(Duration.ofMillis(123));
        DistributionSummary.builder("test.distributionsummary").register(registry).record(24);

        // @formatter:off
        await().atMost(Duration.ofSeconds(10))
            .pollDelay(Duration.ofMillis(100))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> whenPrometheusScraped().then()
                .statusCode(200)
                .contentType(OPENMETRICS_001)
                .body(
                    containsString("test_counter"),
                    endsWith("# EOF\n")
                )
            );

        // tags can vary depending on where you run your tests:
        //  - IDE: no telemetry_sdk_version tag
        //  - Gradle: telemetry_sdk_version has the version number
        whenPrometheusScraped().then().body(
            containsString("{job=\"test\",service_name=\"test\",telemetry_sdk_language=\"java\",telemetry_sdk_name=\"io.micrometer\""),

            matchesPattern("(?s)^.*test_counter\\{.+} 42\\.0\\n.*$"),
            matchesPattern("(?s)^.*test_gauge\\{.+} 12\\.0\\n.*$")

//            matchesPattern("(?s)^.*test_timer_count\\{.+} 1\\n.*$"),
            // TODO: this should be 123ms (0.123) not 123s (123)
            // see: https://github.com/open-telemetry/opentelemetry-collector-contrib/issues/18903
//            matchesPattern("(?s)^.*test_timer_sum\\{.+} 123\\.0\\n.*$"),
//            matchesPattern("(?s)^.*test_timer_bucket\\{.+,le=\"\\+Inf\"} 1\\n.*$"),
//
//            matchesPattern("(?s)^.*test_distributionsummary_count\\{.+} 1\\n.*$"),
//            matchesPattern("(?s)^.*test_distributionsummary_sum\\{.+} 24\\.0\\n.*$"),
//            matchesPattern("(?s)^.*test_distributionsummary_bucket\\{.+,le=\"\\+Inf\"} 1\\n.*$")
        );
        // @formatter:on
    }

}
