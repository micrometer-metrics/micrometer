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
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics.METRIC_NAME_PREFIX;
import static org.apache.kafka.clients.consumer.ConsumerConfig.*;
import static org.assertj.core.api.Assertions.assertThat;

class KafkaClientMetricsConsumerTest {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";

    private final Tags tags = Tags.of("app", "myapp", "version", "1");

    @Test
    void shouldCreateMeters() {
        try (Consumer<String, String> consumer = createConsumer();
                KafkaMetrics metrics = new KafkaClientMetrics(consumer)) {
            MeterRegistry registry = new SimpleMeterRegistry();

            metrics.bindTo(registry);
            assertThat(registry.getMeters()).isNotEmpty()
                .extracting(meter -> meter.getId().getName())
                .allMatch(s -> s.startsWith(METRIC_NAME_PREFIX));
        }
    }

    @Test
    void shouldCreateMetersWithTags() {
        try (Consumer<String, String> consumer = createConsumer();
                KafkaMetrics metrics = new KafkaClientMetrics(consumer, tags)) {
            MeterRegistry registry = new SimpleMeterRegistry();

            metrics.bindTo(registry);

            assertThat(registry.getMeters()).isNotEmpty()
                .extracting(meter -> meter.getId().getTag("app"))
                .allMatch(s -> s.equals("myapp"));
        }
    }

    @Test
    void shouldCreateMetersWithTagsAndCustomScheduler() {
        ScheduledExecutorService customScheduler = Executors.newScheduledThreadPool(1);
        try (Consumer<String, String> consumer = createConsumer();
                KafkaMetrics metrics = new KafkaClientMetrics(consumer, tags, customScheduler)) {
            MeterRegistry registry = new SimpleMeterRegistry();

            metrics.bindTo(registry);

            assertThat(registry.getMeters()).hasSizeGreaterThan(0)
                .extracting(meter -> meter.getId().getTag("app"))
                .allMatch(s -> s.equals("myapp"));

            metrics.close();
            assertThat(customScheduler.isShutdown()).isFalse();
        }
        finally {
            customScheduler.shutdownNow();
            assertThat(customScheduler.isShutdown()).isTrue();
        }
    }

    private Consumer<String, String> createConsumer() {
        Properties consumerConfig = new Properties();
        consumerConfig.put(BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        consumerConfig.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerConfig.put(VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerConfig.put(GROUP_ID_CONFIG, "group");
        return new KafkaConsumer<>(consumerConfig);
    }

}
