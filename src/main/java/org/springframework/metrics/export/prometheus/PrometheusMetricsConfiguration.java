package org.springframework.metrics.export.prometheus;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.metrics.instrument.binder.JvmMemoryMetrics;
import org.springframework.metrics.instrument.prometheus.PrometheusMeterRegistry;

@Configuration
public class PrometheusMetricsConfiguration {
    @Bean
    PrometheusTagFormatter tagFormatter() {
        return new PrometheusTagFormatter();
    }

    @Bean
    PrometheusMeterRegistry meterRegistry() {
        return new PrometheusMeterRegistry();
    }
}
