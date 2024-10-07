/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.docs.observation.messaging;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.observation.transport.SenderContext;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import wiremock.com.google.common.collect.ImmutableMap;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Tag("docker")
class ObservationMessagingIntegrationTest {

    @Container
    private KafkaContainer kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.5.1"));

    private AdminClient adminClient;

    @AfterEach
    void close() {
        if (this.adminClient != null) {
            this.adminClient.close();
        }
    }

    @Test
    void shouldManageProducerAndConsumerMetrics() throws ExecutionException, InterruptedException, TimeoutException {

        // tag::registry_setup[]
        TestObservationRegistry registry = TestObservationRegistry.create();
        // end::registry_setup[]

        // tag::producer_setup[]
        // In Micrometer Tracing we would have predefined
        // PropagatingSenderTracingObservationHandler but for the sake of this demo we
        // create our own handler that puts "foo":"bar" headers into the request and will
        // set the low cardinality key "sent" to "true".
        registry.observationConfig().observationHandler(new HeaderPropagatingHandler());
        // end::producer_setup[]

        String topic = "test";

        // Create topic
        createTopic(topic);

        // tag::producer_side[]
        // Producer side...
        Properties producerConfigs = new Properties();
        producerConfigs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        producerConfigs.put(ProducerConfig.CLIENT_ID_CONFIG, UUID.randomUUID().toString());
        producerConfigs.put(ObservationRegistry.class.getName(), registry);
        producerConfigs.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG,
                "io.micrometer.docs.observation.messaging.ProducerInterceptorConfig");
        Producer<String, String> producer = new KafkaProducer<>(producerConfigs, new StringSerializer(),
                new StringSerializer());

        // Producer sends a message
        producer.send(new ProducerRecord<>(topic, "foo"));
        producer.flush();
        // end::producer_side[]

        // tag::consumer_side[]
        // Consumer side...
        // In Micrometer Tracing we would have predefined
        // PropagatingReceiverTracingObservationHandler but for the sake of this demo we
        // create our own handler that takes the "foo" header's value and sets it as a low
        // cardinality key "received foo header"
        registry.observationConfig().observationHandler(new HeaderReadingHandler());

        Properties consumerConfigs = new Properties();
        consumerConfigs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        consumerConfigs.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        consumerConfigs.put(ObservationRegistry.class.getName(), registry);
        consumerConfigs.put(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG,
                "io.micrometer.docs.observation.messaging.ConsumerInterceptorConfig");
        consumerConfigs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Consumer<String, String> consumer = new KafkaConsumer<>(consumerConfigs, new StringDeserializer(),
                new StringDeserializer());

        // Consumer scubscribes to the topic
        consumer.subscribe(Collections.singletonList(topic));

        // Consumer polls for a message
        consumer.poll(Duration.ofMillis(1000));
        // end::consumer_side[]

        // tag::test_assertions[]
        assertThat(registry).hasObservationWithNameEqualTo("kafka.send")
            .that()
            .hasBeenStarted()
            .hasBeenStopped()
            .hasLowCardinalityKeyValue("sent", "true");

        assertThat(registry).hasObservationWithNameEqualTo("kafka.receive")
            .that()
            .hasBeenStarted()
            .hasBeenStopped()
            .hasLowCardinalityKeyValue("received foo header", "bar");
        // end::test_assertions[]
    }

    private void createTopic(String topic) throws InterruptedException, ExecutionException, TimeoutException {
        AdminClient adminClient = AdminClient
            .create(ImmutableMap.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers()));
        Collection<NewTopic> topics = Collections.singletonList(new NewTopic(topic, 3, (short) 1));
        adminClient.createTopics(topics).all().get(30, TimeUnit.SECONDS);
        this.adminClient = adminClient;
    }

    // tag::kafka_sender_context[]
    static class KafkaSenderContext extends SenderContext<ProducerRecord<String, String>> {

        public KafkaSenderContext(ProducerRecord<String, String> producerRecord) {
            // We describe how the carrier will be mutated (we mutate headers)
            super((carrier, key, value) -> carrier.headers().add(key, value.getBytes(StandardCharsets.UTF_8)));
            setCarrier(producerRecord);
        }

    }
    // end::kafka_sender_context[]

    // tag::kafka_receiver_context[]
    static class KafkaReceiverContext extends ReceiverContext<ConsumerRecords<String, String>> {

        public KafkaReceiverContext(ConsumerRecords<String, String> consumerRecord) {
            // We describe how to read entries from the carrier (we read headers)
            super((carrier, key) -> {
                // This is a very naive approach that takes the first ConsumerRecord
                Header header = carrier.iterator().next().headers().lastHeader(key);
                if (header != null) {
                    return new String(header.value());
                }
                return null;
            });
            setCarrier(consumerRecord);
        }

    }
    // end::kafka_receiver_context[]

    // tag::header_propagating_handler[]
    static class HeaderPropagatingHandler implements ObservationHandler<KafkaSenderContext> {

        @Override
        public void onStart(KafkaSenderContext context) {
            context.getSetter().set(context.getCarrier(), "foo", "bar");
            context.addLowCardinalityKeyValue(KeyValue.of("sent", "true"));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof KafkaSenderContext;
        }

    }
    // end::header_propagating_handler[]

    // tag::header_receiving_handler[]
    static class HeaderReadingHandler implements ObservationHandler<KafkaReceiverContext> {

        @Override
        public void onStart(KafkaReceiverContext context) {
            String fooHeader = context.getGetter().get(context.getCarrier(), "foo");
            // We're setting the value of the <foo> header as a low cardinality key value
            context.addLowCardinalityKeyValue(KeyValue.of("received foo header", fooHeader));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof KafkaReceiverContext;
        }

    }
    // end::header_receiving_handler[]

}
