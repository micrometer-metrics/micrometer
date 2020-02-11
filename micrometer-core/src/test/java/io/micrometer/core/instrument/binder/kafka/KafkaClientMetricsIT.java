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

import static java.lang.System.out;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Tag("docker")
class KafkaClientMetricsIT {
    @Container
    private KafkaContainer kafkaContainer = new KafkaContainer();

    @Test
    void shouldManageProducerAndConsumerMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        assertThat(registry.getMeters()).hasSize(0);

        Properties producerConfigs = new Properties();
        producerConfigs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaContainer.getBootstrapServers());
        Producer<String, String> producer = new KafkaProducer<>(
                producerConfigs, new StringSerializer(), new StringSerializer());

        new KafkaClientMetrics(producer).bindTo(registry);

        int producerMetrics = registry.getMeters().size();
        assertThat(registry.getMeters()).hasSizeGreaterThan(0);
        assertThat(registry.getMeters())
                .extracting(m -> m.getId().getTag("kafka-version"))
                .allMatch(v -> !v.isEmpty());

        Properties consumerConfigs = new Properties();
        consumerConfigs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaContainer.getBootstrapServers());
        consumerConfigs.put(ConsumerConfig.GROUP_ID_CONFIG, "test");
        Consumer<String, String> consumer = new KafkaConsumer<>(
                consumerConfigs, new StringDeserializer(), new StringDeserializer());

        new KafkaClientMetrics(consumer).bindTo(registry);

        //Printing out for discovery purposes
        out.println("Meters from producer before sending:");
        registry.getMeters().forEach(meter -> out.println(meter.getId() + " => " + meter.measure()));

        int producerAndConsumerMetrics = registry.getMeters().size();
        assertThat(registry.getMeters()).hasSizeGreaterThan(producerMetrics);
        assertThat(registry.getMeters())
                .extracting(m -> m.getId().getTag("kafka-version"))
                .allMatch(v -> !v.isEmpty());

        String topic = "test";
        producer.send(new ProducerRecord<>(topic, "key", "value"));
        producer.flush();

        //Printing out for discovery purposes
        out.println("Meters from producer after sending and consumer before poll:");
        registry.getMeters().forEach(meter -> out.println(meter.getId() + " => " + meter.measure()));

        int producerAndConsumerMetricsAfterSend = registry.getMeters().size();
        assertThat(registry.getMeters()).hasSizeGreaterThan(producerAndConsumerMetrics);
        assertThat(registry.getMeters())
                .extracting(m -> m.getId().getTag("kafka-version"))
                .allMatch(v -> !v.isEmpty());

        consumer.subscribe(Collections.singletonList(topic));

        consumer.poll(Duration.ofMillis(100));

        //Printing out for discovery purposes
        out.println("Meters from producer and consumer after polling:");
        registry.getMeters().forEach(meter -> out.println(meter.getId() + " => " + meter.measure()));

        assertThat(registry.getMeters()).hasSizeGreaterThan(producerAndConsumerMetricsAfterSend);
        assertThat(registry.getMeters())
                .extracting(m -> m.getId().getTag("kafka-version"))
                .allMatch(v -> !v.isEmpty());

        //Printing out for discovery purposes
        out.println("All meters from producer and consumer:");
        registry.getMeters().forEach(meter -> out.println(meter.getId() + " => " + meter.measure()));
    }
}
