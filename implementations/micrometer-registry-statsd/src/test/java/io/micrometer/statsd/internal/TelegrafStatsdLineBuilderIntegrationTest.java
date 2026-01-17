/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.statsd.internal;

import com.github.dockerjava.api.model.*;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.statsd.StatsdConfig;
import io.micrometer.statsd.StatsdFlavor;
import io.micrometer.statsd.StatsdMeterRegistry;
import io.micrometer.statsd.StatsdProtocol;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Disabled;
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

import static io.restassured.RestAssured.config;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.containsString;

@Tag("docker")
@Testcontainers
class TelegrafStatsdLineBuilderIntegrationTest {

    private static final Network network = Network.newNetwork();

    @Container
    static GenericContainer<?> influxDB = new GenericContainer<>(DockerImageName.parse("influxdb:latest"))
        .withNetwork(network)
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
    static GenericContainer<?> telegraf = new GenericContainer<>(DockerImageName.parse("telegraf:latest"))
        .withNetwork(network)
        .withExposedPorts(8125)
        .withCopyFileToContainer(MountableFile.forClasspathResource("telegraf-test.conf"),
                "/etc/telegraf/telegraf.conf")
        .dependsOn(influxDB)
        .waitingFor(Wait.forLogMessage(".*Loaded inputs: statsd.*", 1));

    @Issue("#6513")
    @Test
    void shouldSanitizeEqualsSignInTagKey() throws InterruptedException {
        StatsdMeterRegistry registry = getStatsdMeterRegistry(5000);

        Counter.builder("test=metric").tag("this=is=the", "tag=test").register(registry).increment();
        registry.close();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            whenGetMetricFromInfluxDb("test=metric").then()
                .statusCode(200)
                .body(containsString("this_is_the"), containsString("tag=test"));
        });
    }

    @Test
    void shouldSanitizeCommaInTagKeyAndValue() throws InterruptedException {
        StatsdMeterRegistry registry = getStatsdMeterRegistry(5000);

        Counter.builder("test,metric").tag("comma,key", "comma,value").register(registry).increment();
        registry.close();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            whenGetMetricFromInfluxDb("test_metric").then()
                .statusCode(200)
                .body(containsString("comma_key"), containsString("comma_value"));
        });
    }

    @Test
    void shouldSanitizeSpaceInTagKeyAndValue() throws InterruptedException {
        StatsdMeterRegistry registry = getStatsdMeterRegistry(5000);

        Counter.builder("test metric").tag("space key", "space value").register(registry).increment();
        registry.close();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            whenGetMetricFromInfluxDb("test_metric").then()
                .statusCode(200)
                .body(containsString("space_key"), containsString("space_value"));
        });
    }

    private Response whenGetMetricFromInfluxDb(String metricName) {
        String fluxQuery = String.format(
                "from(bucket: \"metrics_db\") |> range(start: -1h) |> filter(fn: (r) => r._measurement == \"%s\")",
                metricName);

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

    private StatsdMeterRegistry getStatsdMeterRegistry(long registryWarmUpMs) throws InterruptedException {
        StatsdConfig statsdConfig = getStatsdConfig();
        StatsdMeterRegistry registry = new StatsdMeterRegistry(statsdConfig, Clock.SYSTEM);
        Thread.sleep(registryWarmUpMs);

        return registry;
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
                return telegraf.getFirstMappedPort();
            }

            @Override
            public @NonNull StatsdProtocol protocol() {
                return StatsdProtocol.TCP;
            }
        };
    }

}
