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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import static org.apache.kafka.clients.producer.ProducerConfig.*;

class KafkaProducerMetricsTest {
  private final static String BOOTSTRAP_SERVERS = "localhost:9092";
  private Tags tags = Tags.of("app", "myapp", "version", "1");

  @Test void verify() {
    try (Producer<String, String> producer = createProducer()) {
      KafkaMetrics metrics = new KafkaMetrics(producer, tags);
      MeterRegistry registry = new SimpleMeterRegistry();

      metrics.bindTo(registry);

      registry.get("kafka.producer.metrics.batch.size.max").tags(tags).gauge();
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
