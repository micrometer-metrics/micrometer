/*
 * Copyright 2026 VMware, Inc.
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
package io.micrometer.statsd;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.hamcrest.Matcher;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static io.restassured.RestAssured.config;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

@Tag("docker")
@Testcontainers
class TelegrafStatsdLineBuilderIntegrationTest {

    // Required to resolve the InfluxDB port,
    // as the mapped port is unknown during static initialization.
    private static final Network network = Network.newNetwork();

    private static final DockerImageName INFLUXDB_IMAGE = DockerImageName
        .parse("influxdb:" + getImageVersion("influxdb-image.version"));

    private static final DockerImageName TELEGRAF_IMAGE = DockerImageName
        .parse("telegraf:" + getImageVersion("telegraf-image.version"));

    @AfterAll
    static void tearDown() {
        network.close();
    }

    @Container
    static GenericContainer<?> influxDB = new GenericContainer<>(INFLUXDB_IMAGE).withNetwork(network)
        .withNetworkAliases("influxdb")
        .withExposedPorts(8086)
        .withEnv("DOCKER_INFLUXDB_INIT_MODE", "setup")
        .withEnv("DOCKER_INFLUXDB_INIT_USERNAME", "admin")
        .withEnv("DOCKER_INFLUXDB_INIT_PASSWORD", "password")
        .withEnv("DOCKER_INFLUXDB_INIT_ORG", "my-org")
        .withEnv("DOCKER_INFLUXDB_INIT_BUCKET", "metrics_db")
        .withEnv("DOCKER_INFLUXDB_INIT_ADMIN_TOKEN", "my-test-token")
        .waitingFor(Wait.forHttp("/ping").forStatusCode(204));

    @Container
    static GenericContainer<?> telegraf = new GenericContainer<>(TELEGRAF_IMAGE).withNetwork(network)
        .withExposedPorts(8125)
        .withCopyFileToContainer(MountableFile.forClasspathResource("telegraf.conf"), "/etc/telegraf/telegraf.conf")
        .dependsOn(influxDB)
        .waitingFor(Wait.forListeningPorts(8125));

    private static String getImageVersion(String systemProperty) {
        String version = System.getProperty(systemProperty);
        if (version == null) {
            throw new IllegalStateException("System property '" + systemProperty
                    + "' is not set. This should be set in the build configuration for running from the command line. If you are running TelegrafStatsdLineBuilderIntegrationTest from an IDE, set the system property to the desired collector image version.");
        }
        return version;
    }

    @Issue("#6513")
    @Test
    void shouldSanitizeEqualsSignInTagKey() {
        registerMeter(registry -> Counter.builder("test=metric=equal")
            .tag("this=is=the", "tag=test")
            .register(registry)
            .increment());

        await().alias("Telegraf flushing and InfluxDB ingestion")
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(
                    () -> verifyMetric("test=metric=equal", containsString("this_is_the"), containsString("tag=test")));
    }

    @Test
    void shouldSanitizeCommaInTagKeyAndValue() {
        registerMeter(registry -> Counter.builder("test,metric,comma")
            .tag("comma,key", "comma,value")
            .register(registry)
            .increment());

        await().alias("Telegraf flushing and InfluxDB ingestion")
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> verifyMetric("test_metric_comma", containsString("comma_key"),
                    containsString("comma_value")));
    }

    @Test
    void shouldSanitizeSpaceInTagKeyAndValue() {
        registerMeter(registry -> Counter.builder("test metric space")
            .tag("space key", "space value")
            .register(registry)
            .increment());

        await().alias("Telegraf flushing and InfluxDB ingestion")
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> verifyMetric("test_metric_space", containsString("space_key"),
                    containsString("space_value")));
    }

    private void registerMeter(Consumer<StatsdMeterRegistry> metricAction) {
        StatsdConfig statsdConfig = getStatsdConfig();
        StatsdMeterRegistry registry = new StatsdMeterRegistry(statsdConfig, Clock.SYSTEM);
        waitForClientReady(registry);
        metricAction.accept(registry);
        registry.close();
    }

    private void verifyMetric(String expectedMetricName, Matcher<? super String>... expectedTags) {
        whenGetMetricFromInfluxDb(getFluxQuery(expectedMetricName)).then().statusCode(200).body(allOf(expectedTags));
    }

    private String getFluxQuery(String metricName) {
        return String.format(
                "from(bucket: \"metrics_db\") |> range(start: -1h) |> filter(fn: (r) => r._measurement == \"%s\")",
                metricName);
    }

    private Response whenGetMetricFromInfluxDb(String fluxQuery) {
        return given()
            .config(config().encoderConfig(
                    EncoderConfig.encoderConfig().encodeContentTypeAs("application/vnd.flux", ContentType.TEXT)))
            .port(influxDB.getFirstMappedPort())
            .header("Authorization", "Token my-test-token")
            .queryParam("org", "my-org")
            .contentType("application/vnd.flux")
            .accept("application/csv")
            .body(fluxQuery)
            .when()
            .post("/api/v2/query");
    }

    private void waitForClientReady(StatsdMeterRegistry meterRegistry) {
        await().until(() -> !clientIsDisposed(meterRegistry));
    }

    private boolean clientIsDisposed(StatsdMeterRegistry meterRegistry) {
        return meterRegistry.statsdConnection.get().isDisposed();
    }

    private StatsdConfig getStatsdConfig() {
        return new StatsdConfig() {
            @Override
            @Nullable public String get(@NonNull String key) {
                return null;
            }

            @Override
            public @NonNull StatsdFlavor flavor() {
                return StatsdFlavor.TELEGRAF;
            }

            @Override
            public @NonNull String host() {
                return telegraf.getHost();
            }

            @Override
            public int port() {
                return telegraf.getMappedPort(8125);
            }

            @Override
            public @NonNull StatsdProtocol protocol() {
                return StatsdProtocol.TCP;
            }
        };
    }

}
