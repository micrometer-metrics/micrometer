/**
 * Copyright 2018 Pivotal Software, Inc.
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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link KafkaConsumerMetrics}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
class KafkaConsumerMetricsTest {
    private final static String TOPIC = "my-example-topic";
    private final static String BOOTSTRAP_SERVERS = "localhost:9092";

    private final KafkaConsumerMetrics kafkaConsumerMetrics = new KafkaConsumerMetrics();

    private static Consumer<Long, String> createConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "MicrometerTestConsumer");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        Consumer<Long, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(TOPIC));
        return consumer;
    }

    @Test
    void consumerMetrics() {
        Consumer<Long, String> consumer1 = createConsumer();
        Consumer<Long, String> consumer2 = createConsumer();

        MeterRegistry registry = new SimpleMeterRegistry();
        kafkaConsumerMetrics.bindTo(registry);

        // consumer group metrics
        Gauge assignedPartitions = registry.get("kafka.consumer.assigned.partitions").gauge();
        assertThat(assignedPartitions.getId().getTag("client.id")).startsWith("consumer-");

        // global connection metrics
        Gauge connectionCount = registry.get("kafka.consumer.connection.count").gauge();
        assertThat(connectionCount.getId().getTag("client.id")).startsWith("consumer-");

        consumer1.close();
        consumer2.close();
    }

    @Test
    void kafkaMajorVersion() {
        Consumer<Long, String> consumer = createConsumer();

        assertThat(kafkaConsumerMetrics.kafkaMajorVersion(Tags.of("client.id", "consumer-1"))).isGreaterThanOrEqualTo(2);

        consumer.close();
    }

    @Test
    void newConsumersAreDiscoveredByListener() throws InterruptedException {
        MeterRegistry registry = new SimpleMeterRegistry();
        kafkaConsumerMetrics.bindTo(registry);

        CountDownLatch latch = new CountDownLatch(1);
        registry.config().onMeterAdded(m -> {
            if (m.getId().getName().contains("kafka"))
                latch.countDown();
        });

        Consumer<Long, String> consumer = createConsumer();

        latch.await(10, TimeUnit.SECONDS);

        consumer.close();
    }

    @Test
    void consumerBeforeBindingWhenClosedShouldRemoveMeters() {
        Consumer<Long, String> consumer = createConsumer();

        MeterRegistry registry = new SimpleMeterRegistry();
        kafkaConsumerMetrics.bindTo(registry);

        Gauge gauge = registry.get("kafka.consumer.assigned.partitions").gauge();
        assertThat(gauge.getId().getTag("client.id")).startsWith("consumer-");

        consumer.close();
        assertThat(registry.find("kafka.consumer.assigned.partitions").gauge()).isNull();
    }

    @Test
    void consumerAfterBindingWhenClosedShouldRemoveMeters() {
        MeterRegistry registry = new SimpleMeterRegistry();
        kafkaConsumerMetrics.bindTo(registry);

        Consumer<Long, String> consumer = createConsumer();
        Gauge gauge = registry.get("kafka.consumer.assigned.partitions").gauge();
        assertThat(gauge.getId().getTag("client.id")).startsWith("consumer-");

        consumer.close();
        assertThat(registry.find("kafka.consumer.assigned.partitions").gauge()).isNull();
    }
}
