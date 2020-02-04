/**
 * Copyright 2020 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.kafka;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@Tag("docker")
class KafkaClientMetricsIT {
    @Container
    private KafkaContainer kafkaContainer = new KafkaContainer("5.3.0");

    @Test
    void shouldManageProducerAndConsumerMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        assertEquals(0, registry.getMeters().size());

        Properties producerConfigs = new Properties();
        producerConfigs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaContainer.getBootstrapServers());
        Producer<String, String> producer = new KafkaProducer<>(
                producerConfigs, new StringSerializer(), new StringSerializer());

        new KafkaMetrics(producer).bindTo(registry);

        int producerMetrics = registry.getMeters().size();
        assertTrue(producerMetrics > 0);

        Properties consumerConfigs = new Properties();
        consumerConfigs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaContainer.getBootstrapServers());
        consumerConfigs.put(ConsumerConfig.GROUP_ID_CONFIG, "test");
        Consumer<String, String> consumer = new KafkaConsumer<>(
                consumerConfigs, new StringDeserializer(), new StringDeserializer());

        new KafkaMetrics(consumer).bindTo(registry);

        int producerAndConsumerMetrics = registry.getMeters().size();
        assertTrue(producerAndConsumerMetrics > producerMetrics);

        String topic = "test";
        producer.send(new ProducerRecord<>(topic, "key", "value"));
        producer.flush();

        registry.getMeters().forEach(meter -> {
            System.out.println(meter.getId() + " => " + meter.measure());
        });

        int producerAndConsumerMetricsAfterSend = registry.getMeters().size();
        assertTrue(producerAndConsumerMetricsAfterSend > producerAndConsumerMetrics);

        consumer.subscribe(Collections.singletonList(topic));

        consumer.poll(Duration.ofMillis(100));

        registry.getMeters().forEach(meter -> {
            System.out.println(meter.getId() + " => " + meter.measure());
        });

        int producerAndConsumerMetricsAfterPoll = registry.getMeters().size();
        assertTrue(producerAndConsumerMetricsAfterPoll > producerAndConsumerMetricsAfterSend);

        registry.getMeters().forEach(meter -> {
            System.out.println(meter.getId() + " => " + meter.measure());
        });
    }
}
