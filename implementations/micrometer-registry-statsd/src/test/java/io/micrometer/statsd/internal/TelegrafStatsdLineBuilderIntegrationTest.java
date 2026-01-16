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
package io.micrometer.statsd.internal;

import com.github.dockerjava.api.model.*;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.statsd.StatsdConfig;
import io.micrometer.statsd.StatsdFlavor;
import io.micrometer.statsd.StatsdMeterRegistry;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("docker")
@Testcontainers
class TelegrafStatsdLineBuilderIntegrationTest {

    static private final Network network = Network.newNetwork();
    static private final ExposedPort telegrafExposedPort = new ExposedPort(8125, InternetProtocol.UDP);
    static private final String influxDbName = "metrics_db";
    static private final Integer influxDbPort = 8086;

    @Container
    static GenericContainer<?> influxDB = new GenericContainer<>(DockerImageName.parse("influxdb:1.8"))
        .withNetwork(network)
        .withNetworkAliases("influxdb")
        .withExposedPorts(influxDbPort)
        .withEnv("INFLUXDB_DB", influxDbName)
        .waitingFor(Wait.forHttp("/ping").forStatusCode(204));

    @Container
    static GenericContainer<?> telegraf = new GenericContainer<>(DockerImageName.parse("telegraf:latest"))
        .withNetwork(network)
        .withCreateContainerCmdModifier(cmd -> {
            HostConfig hostConfig = cmd.getHostConfig() == null ? new HostConfig() : cmd.getHostConfig();
            PortBinding portBinding = new PortBinding(Ports.Binding.bindPort(0), telegrafExposedPort);
            cmd.withHostConfig(hostConfig.withPortBindings(portBinding));
            cmd.withExposedPorts(telegrafExposedPort);
        })
        .withCopyFileToContainer(MountableFile.forClasspathResource("telegraf-test.conf"),
                "/etc/telegraf/telegraf.conf")
        .dependsOn(influxDB)
        .waitingFor(Wait.forLogMessage(".*Loaded inputs: statsd.*", 1));

    @Test
    void shouldSanitizeEqualsSignInTagKeys() throws InterruptedException {
        StatsdMeterRegistry registry = getStatsdMeterRegistry(5000);

        try {
            Counter counter = Counter.builder("metric").tag("this=is=the", "tag=test").register(registry);
            counter.increment();

            await().atMost(60, TimeUnit.SECONDS).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
                String queryResult = queryInfluxDB("SELECT * FROM \"metric\"");

                assertThat(queryResult).contains("\"name\":\"metric\"");
                assertThat(queryResult).contains("\"this\"");
                assertThat(queryResult).contains("\"is=the=tag=test\"");
            });
        }
        finally {
            registry.close();
        }
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
            @Nullable public String get(@NotNull String key) {
                return null;
            }

            @Override
            public @NotNull StatsdFlavor flavor() {
                return StatsdFlavor.TELEGRAF;
            }

            @Override
            public @NotNull String host() {
                return telegraf.getHost();
            }

            @Override
            public int port() {
                Ports.Binding[] bindings = telegraf.getContainerInfo()
                    .getNetworkSettings()
                    .getPorts()
                    .getBindings()
                    .get(telegrafExposedPort);
                Ports.Binding binding = Objects.requireNonNull(bindings)[0];
                return Integer.parseInt(binding.getHostPortSpec());
            }

            @Override
            public @NotNull Duration step() {
                return Duration.ofSeconds(1);
            }
        };
    }

    private String queryInfluxDB(String query) {
        try {
            String baseUrl = "http://" + influxDB.getHost() + ":" + influxDB.getMappedPort(influxDbPort);

            String fullUrl = baseUrl + "/query?db=" + influxDbName + "&q="
                    + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);

            HttpURLConnection con = (HttpURLConnection) URI.create(fullUrl).toURL().openConnection();
            con.setRequestMethod("GET");

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines().collect(java.util.stream.Collectors.joining());
            }
        }
        catch (Exception e) {
            return "";
        }
    }

}
