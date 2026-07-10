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

import io.micrometer.docs.observation.messaging.ObservationMessagingIntegrationTest.KafkaSenderContext;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.Map;

// tag::producer_interceptor_config[]
public class ProducerInterceptorConfig implements ProducerInterceptor<String, String> {

    private ObservationRegistry observationRegistry;

    private Observation observation;

    @Override
    public ProducerRecord<String, String> onSend(ProducerRecord<String, String> record) {
        // This code will be called before the message gets sent. We create
        // a context and pass it to an Observation. Upon start, the handler will be called
        // and the ProducerRecord will be mutated
        KafkaSenderContext context = new KafkaSenderContext(record);
        this.observation = Observation.start("kafka.send", () -> context, observationRegistry);
        // We return the mutated carrier
        return context.getCarrier();
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        // Once the message got sent (with or without an exception) we attach an exception
        // and stop the observation
        this.observation.error(exception);
        this.observation.stop();
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

// end::producer_interceptor_config[]
