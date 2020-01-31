package io.micrometer.core.instrument.binder.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Properties;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;

//TODO to be renamed once old instrumentation is removed.
class NewKafkaConsumerMetricsTest {
  private final static String BOOTSTRAP_SERVERS = "localhost:9092";
  private Tags tags = Tags.of("app", "myapp", "version", "1");

  @Test void verify() {
    try (Consumer<String, String> consumer = createConsumer()) {
      KafkaMetrics metrics = new KafkaMetrics(consumer, tags);
      MeterRegistry registry = new SimpleMeterRegistry();

      metrics.bindTo(registry);

      registry.get("kafka.consumer.metrics.request.total").tags(tags).functionCounter();
    }
  }

  private Consumer<String, String> createConsumer() {
    Properties consumerConfig = new Properties();
    consumerConfig.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
    consumerConfig.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    consumerConfig.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, "group");
    return new KafkaConsumer<>(consumerConfig);
  }
}
