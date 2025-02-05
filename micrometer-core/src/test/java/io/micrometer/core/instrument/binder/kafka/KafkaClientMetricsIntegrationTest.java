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

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static java.lang.System.out;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Tag("docker")
class KafkaClientMetricsIntegrationTest {

    @Container
    private KafkaContainer kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.5.1"));

    @Test
    void shouldManageProducerAndConsumerMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        assertThat(registry.getMeters()).hasSize(0);

        // tag::producer_setup[]
        Properties producerConfigs = new Properties();
        producerConfigs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        Producer<String, String> producer = new KafkaProducer<>(producerConfigs, new StringSerializer(),
                new StringSerializer());

        KafkaClientMetrics producerKafkaMetrics = new KafkaClientMetrics(producer);
        producerKafkaMetrics.bindTo(registry);
        // end::producer_setup[]

        int producerMetrics = registry.getMeters().size();
        assertThat(registry.getMeters()).isNotEmpty();
        assertThat(registry.getMeters()).extracting(m -> m.getId().getTag("kafka.version")).allMatch(v -> !v.isEmpty());

        // tag::consumer_setup[]
        Properties consumerConfigs = new Properties();
        consumerConfigs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        consumerConfigs.put(ConsumerConfig.GROUP_ID_CONFIG, "test");
        Consumer<String, String> consumer = new KafkaConsumer<>(consumerConfigs, new StringDeserializer(),
                new StringDeserializer());

        KafkaClientMetrics consumerKafkaMetrics = new KafkaClientMetrics(consumer);
        consumerKafkaMetrics.bindTo(registry);
        // end::consumer_setup[]

        // Printing out for discovery purposes
        out.println("Meters from producer before sending:");
        printMeters(registry);

        int producerAndConsumerMetrics = registry.getMeters().size();
        assertThat(registry.getMeters()).hasSizeGreaterThan(producerMetrics);
        assertThat(registry.getMeters()).extracting(m -> m.getId().getTag("kafka.version")).allMatch(v -> !v.isEmpty());

        String topic = "test";
        producer.send(new ProducerRecord<>(topic, "key", "value"));
        producer.flush();

        // Printing out for discovery purposes
        out.println("Meters from producer after sending and consumer before poll:");
        printMeters(registry);

        producerKafkaMetrics.checkAndBindMetrics(registry);

        int producerAndConsumerMetricsAfterSend = registry.getMeters().size();
        assertThat(registry.getMeters()).hasSizeGreaterThan(producerAndConsumerMetrics);
        assertThat(registry.getMeters()).extracting(m -> m.getId().getTag("kafka.version")).allMatch(v -> !v.isEmpty());

        consumer.subscribe(Collections.singletonList(topic));

        consumer.poll(Duration.ofMillis(100));

        // Printing out for discovery purposes
        out.println("Meters from producer and consumer after polling:");
        printMeters(registry);

        consumerKafkaMetrics.checkAndBindMetrics(registry);

        assertThat(registry.getMeters()).hasSizeGreaterThan(producerAndConsumerMetricsAfterSend);
        assertThat(registry.getMeters()).extracting(m -> m.getId().getTag("kafka.version")).allMatch(v -> !v.isEmpty());

        // see gh-3300
        assertThat(registry.getMeters().stream().filter(meter -> meter.getId().getName().endsWith(".count")))
            .allMatch(meter -> meter instanceof Gauge);

        // Printing out for discovery purposes
        out.println("All meters from producer and consumer:");
        printMeters(registry);

        List<Meter> metersEndingWithTotal = registry.getMeters()
            .stream()
            .filter(meter -> meter.getId().getName().endsWith(".total"))
            .collect(Collectors.toList());
        List<Meter> functionCounters = registry.getMeters()
            .stream()
            .filter(meter -> meter instanceof FunctionCounter)
            .collect(Collectors.toList());
        assertThat(metersEndingWithTotal).isEqualTo(functionCounters);

        producerKafkaMetrics.close();
        consumerKafkaMetrics.close();
    }

    @Test
    void shouldRegisterMetricsFromDifferentClients() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        assertThat(registry.getMeters()).hasSize(0);

        Properties producer1Configs = new Properties();
        producer1Configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        producer1Configs.put(ProducerConfig.CLIENT_ID_CONFIG, "producer1");
        Producer<String, String> producer1 = new KafkaProducer<>(producer1Configs, new StringSerializer(),
                new StringSerializer());

        KafkaClientMetrics producer1KafkaMetrics = new KafkaClientMetrics(producer1);
        producer1KafkaMetrics.bindTo(registry);

        int producer1Metrics = registry.getMeters().size();
        assertThat(producer1Metrics).isGreaterThan(0);

        producer1.send(new ProducerRecord<>("topic1", "foo"));
        producer1.flush();

        producer1KafkaMetrics.checkAndBindMetrics(registry);

        int producer1MetricsAfterSend = registry.getMeters().size();
        assertThat(producer1MetricsAfterSend).isGreaterThan(producer1Metrics);

        Properties producer2Configs = new Properties();
        producer2Configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        producer2Configs.put(ProducerConfig.CLIENT_ID_CONFIG, "producer2");
        Producer<String, String> producer2 = new KafkaProducer<>(producer2Configs, new StringSerializer(),
                new StringSerializer());

        KafkaClientMetrics producer2KafkaMetrics = new KafkaClientMetrics(producer2);
        producer2KafkaMetrics.bindTo(registry);

        producer2.send(new ProducerRecord<>("topic1", "foo"));
        producer2.flush();

        producer2KafkaMetrics.checkAndBindMetrics(registry);

        int producer2MetricsAfterSend = registry.getMeters().size();
        assertThat(producer2MetricsAfterSend).isEqualTo(producer1MetricsAfterSend * 2);
    }

    void printMeters(SimpleMeterRegistry registry) {
        registry.getMeters().forEach(meter -> out.println(meter.getId() + " => " + meter.measure()));
    }

}
