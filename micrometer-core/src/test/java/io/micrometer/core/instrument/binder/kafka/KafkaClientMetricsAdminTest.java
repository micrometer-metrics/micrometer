/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.binder.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics.METRIC_NAME_PREFIX;
import static org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;

class KafkaClientMetricsAdminTest {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";

    private final Tags tags = Tags.of("app", "myapp", "version", "1");

    KafkaClientMetrics metrics;

    @AfterEach
    void afterEach() {
        if (metrics != null)
            metrics.close();
    }

    @Test
    void shouldCreateMeters() {
        try (AdminClient adminClient = createAdmin()) {
            metrics = new KafkaClientMetrics(adminClient);
            MeterRegistry registry = new SimpleMeterRegistry();

            metrics.bindTo(registry);
            assertThat(registry.getMeters()).isNotEmpty()
                .extracting(meter -> meter.getId().getName())
                .allMatch(s -> s.startsWith(METRIC_NAME_PREFIX));
        }
    }

    @Test
    void shouldCreateMetersWithTags() {
        try (AdminClient adminClient = createAdmin()) {
            metrics = new KafkaClientMetrics(adminClient, tags);
            MeterRegistry registry = new SimpleMeterRegistry();

            metrics.bindTo(registry);

            assertThat(registry.getMeters()).isNotEmpty()
                .extracting(meter -> meter.getId().getTag("app"))
                .allMatch(s -> s.equals("myapp"));
        }
    }

    @Test
    void shouldCreateMetersWithTagsAndCustomScheduler() {
        try (AdminClient adminClient = createAdmin()) {
            ScheduledExecutorService customScheduler = Executors.newScheduledThreadPool(1);
            metrics = new KafkaClientMetrics(adminClient, tags, customScheduler);
            MeterRegistry registry = new SimpleMeterRegistry();

            metrics.bindTo(registry);

            assertThat(registry.getMeters()).hasSizeGreaterThan(0)
                .extracting(meter -> meter.getId().getTag("app"))
                .allMatch(s -> s.equals("myapp"));

            metrics.close();
            assertThat(customScheduler.isShutdown()).isFalse();

            customScheduler.shutdownNow();
            assertThat(customScheduler.isShutdown()).isTrue();
        }
    }

    private AdminClient createAdmin() {
        Properties adminConfig = new Properties();
        adminConfig.put(BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        return AdminClient.create(adminConfig);
    }

}
