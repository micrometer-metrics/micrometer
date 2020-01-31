package io.micrometer.core.instrument.binder.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Properties;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.Test;

class KafkaAdminMetricsTest {
  private final static String BOOTSTRAP_SERVERS = "localhost:9092";
  private Tags tags = Tags.of("app", "myapp", "version", "1");

  @Test void verify() {
    try (AdminClient adminClient = createAdmin()) {
      KafkaMetrics metrics = new KafkaMetrics(adminClient, tags);
      MeterRegistry registry = new SimpleMeterRegistry();

      metrics.bindTo(registry);

      registry.get("kafka.admin.client.metrics.connection.close.total").tags(tags).functionCounter();
    }
  }

  private AdminClient createAdmin() {
    Properties adminConfig = new Properties();
    adminConfig.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
    return AdminClient.create(adminConfig);
  }
}
