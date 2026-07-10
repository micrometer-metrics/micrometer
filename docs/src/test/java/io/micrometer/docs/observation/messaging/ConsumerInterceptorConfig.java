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

import io.micrometer.docs.observation.messaging.ObservationMessagingIntegrationTest.KafkaReceiverContext;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

import java.util.Map;

// tag::consumer_interceptor_config[]
public class ConsumerInterceptorConfig implements ConsumerInterceptor<String, String> {

    private ObservationRegistry observationRegistry;

    @Override
    public ConsumerRecords<String, String> onConsume(ConsumerRecords<String, String> records) {
        // We're creating the receiver context
        KafkaReceiverContext context = new KafkaReceiverContext(records);
        // Then, we're just starting and stopping the observation on the consumer side
        Observation.start("kafka.receive", () -> context, observationRegistry).stop();
        // We could put the Observation in scope so that the users can propagate it
        // further on
        return context.getCarrier();
    }

    @Override
    public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {

    }

    @Override
    public void close() {

    }

    @Override
    public void configure(Map<String, ?> configs) {
        // We retrieve the ObservationRegistry from the configuration
        this.observationRegistry = (ObservationRegistry) configs.get(ObservationRegistry.class.getName());
    }

}
// end::consumer_interceptor_config[]
