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
    producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
    producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    return new KafkaProducer<>(producerConfig);
  }
}
