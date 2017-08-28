/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.spring.export.prometheus;

import io.micrometer.core.instrument.Clock;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.spring.export.MetricsExporter;
import io.prometheus.client.CollectorRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "io.micrometer.prometheus.PrometheusMeterRegistry")
public class PrometheusExportConfiguration {
    @ConditionalOnProperty(value = "metrics.prometheus.enabled", matchIfMissing = true)
    @Bean
    MetricsExporter prometheusExporter(CollectorRegistry collectorRegistry, Clock clock) {
        return () -> new PrometheusMeterRegistry(collectorRegistry, clock);
    }

    @ConditionalOnMissingBean
    @Bean
    CollectorRegistry collectorRegistry() {
        return new CollectorRegistry(true);
    }

    @ConditionalOnMissingBean
    @Bean
    Clock clock() {
        return Clock.SYSTEM;
    }

    @ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.Endpoint")
    @Configuration
    static class PrometheusScrapeEndpointConfiguration {
        @Bean
        public PrometheusActuatorEndpoint prometheusEndpoint(CollectorRegistry collectorRegistry) {
            return new PrometheusActuatorEndpoint(collectorRegistry);
        }
    }
}
