package io.micrometer.spring.autoconfigure.kafka.consumer;

import io.micrometer.core.instrument.binder.kafka.KafkaConsumerMetrics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(value = "management.metrics.kafka.consumer.enabled", matchIfMissing = true)
public class KafkaConsumerMetricsConfiguration {

    @Bean
    public KafkaConsumerMetrics kafkaConsumerMetrics() {
        return new KafkaConsumerMetrics();
    }
}
