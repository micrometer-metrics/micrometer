/**
 * Copyright 2018 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.kafka;

import com.salesforce.kafka.test.junit5.SharedKafkaTestResource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

class KafkaConsumerMetricsTest {
    private final static String TOPIC = "my-example-topic";

    private final KafkaConsumerMetrics kafkaConsumerMetrics = new KafkaConsumerMetrics();

    @RegisterExtension
    public static final SharedKafkaTestResource sharedKafkaTestResource = new SharedKafkaTestResource()
        // Start a cluster with 1 brokers.
        .withBrokers(1);

    private Consumer<Long, String> createConsumer(String groupId, String clientId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, sharedKafkaTestResource.getKafkaConnectString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId);

        Consumer<Long, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(TOPIC));
        return consumer;
    }

    private void sendMessage() throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, sharedKafkaTestResource.getKafkaConnectString());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, LongSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        Producer<Long, String> producer = new KafkaProducer<>(props);

        producer.send(new ProducerRecord<>(TOPIC, "Hello")).get();
        producer.close();

    }

    @Test
    void consumerMetrics() {
        createConsumer("Test", "C1");
        createConsumer("Test", "C2");

        MeterRegistry registry = new SimpleMeterRegistry();
        kafkaConsumerMetrics.bindTo(registry);

        // fetch metrics
        registry.get("kafka.consumer.records.lag.max").tag("client.id", "C1").gauge();
        registry.get("kafka.consumer.records.lag.max").tag("client.id", "C2").gauge();

        // consumer group metrics
        registry.get("kafka.consumer.assigned.partitions").tag("client.id", "C1").gauge();

        // global connection metrics
        registry.get("kafka.consumer.connection.count").tag("client.id", "C1").gauge();
    }

    @Test
    void consumerMetricsWithConsumerStartAfterBinding() {

        MeterRegistry registry = new SimpleMeterRegistry();
        kafkaConsumerMetrics.bindTo(registry);

        createConsumer("Test", "C4");
        createConsumer("Test", "C5");


        // fetch metrics
        registry.get("kafka.consumer.records.lag.max").tag("client.id", "C4").gauge();
        registry.get("kafka.consumer.records.lag.max").tag("client.id", "C5").gauge();

        // consumer group metrics
        registry.get("kafka.consumer.assigned.partitions").tag("client.id", "C4").gauge();

        // global connection metrics
        registry.get("kafka.consumer.connection.count").tag("client.id", "C4").gauge();
    }

    @Test
    void consumerRecordCountMetrics() throws Exception {

        MeterRegistry registry = new SimpleMeterRegistry();
        kafkaConsumerMetrics.bindTo(registry);

        // Two consumers - different groups - same message will be received in both
        Consumer<Long, String> consumer1 = createConsumer("Test1", "C6");
        Consumer<Long, String> consumer2 = createConsumer("Test2", "C7");

        sendMessage();

        ConsumerRecords<Long, String> recordInFirstGroup = consumer1.poll(Duration.ofSeconds(10));
        ConsumerRecords<Long, String> recordInSecondGroup = consumer2.poll(Duration.ofSeconds(10));


        double countFirstConsumer = registry.get("kafka.consumer.records.consumed.total").tag("client.id", "C6").functionCounter().count();
        double countSecondConsumer = registry.get("kafka.consumer.records.consumed.total").tag("client.id", "C7").functionCounter().count();

        assertEquals(recordInFirstGroup.count(), countFirstConsumer, 0);
        assertEquals(recordInSecondGroup.count(), countSecondConsumer, 0);

    }

    @Test
    void kafkaMajorVersion() {
        createConsumer("Test", "C3");
        assertThat(kafkaConsumerMetrics.kafkaMajorVersion(Tags.of("client.id", "C3"))).isGreaterThanOrEqualTo(2);
    }
}
