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
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics.METRIC_NAME_PREFIX;
import static org.apache.kafka.clients.producer.ProducerConfig.*;
import static org.assertj.core.api.Assertions.assertThat;

class KafkaClientMetricsProducerTest {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";

    private Tags tags = Tags.of("app", "myapp", "version", "1");

    KafkaClientMetrics metrics;

    @AfterEach
    void afterEach() {
        if (metrics != null)
            metrics.close();
    }

    @Test
    void shouldCreateMeters() {
        try (Producer<String, String> producer = createProducer()) {
            metrics = new KafkaClientMetrics(producer);
            MeterRegistry registry = new SimpleMeterRegistry();

            metrics.bindTo(registry);
            assertThat(registry.getMeters()).isNotEmpty()
                .extracting(meter -> meter.getId().getName())
                .allMatch(s -> s.startsWith(METRIC_NAME_PREFIX));
        }
    }

    @Test
    void shouldCreateMetersWithTags() {
        try (Producer<String, String> producer = createProducer()) {
            metrics = new KafkaClientMetrics(producer, tags);
            MeterRegistry registry = new SimpleMeterRegistry();

            metrics.bindTo(registry);

            assertThat(registry.getMeters()).isNotEmpty()
                .extracting(meter -> meter.getId().getTag("app"))
                .allMatch(s -> s.equals("myapp"));
        }
    }

    private Producer<String, String> createProducer() {
        Properties producerConfig = new Properties();
        producerConfig.put(BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        producerConfig.put(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerConfig.put(VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaProducer<>(producerConfig);
    }

}
