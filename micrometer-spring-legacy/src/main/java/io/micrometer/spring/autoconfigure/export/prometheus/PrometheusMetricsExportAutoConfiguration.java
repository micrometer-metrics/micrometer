/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.spring.autoconfigure.export.prometheus;

import io.micrometer.core.instrument.Clock;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.spring.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import io.micrometer.spring.autoconfigure.MetricsAutoConfiguration;
import io.micrometer.spring.autoconfigure.export.StringToDurationConverter;
import io.micrometer.spring.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import io.micrometer.spring.export.prometheus.PrometheusPushGatewayManager;
import io.micrometer.spring.export.prometheus.PrometheusScrapeEndpoint;
import io.micrometer.spring.export.prometheus.PrometheusScrapeMvcEndpoint;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.PushGateway;
import org.springframework.boot.actuate.autoconfigure.ManagementContextConfiguration;
import org.springframework.boot.actuate.condition.ConditionalOnEnabledEndpoint;
import org.springframework.boot.actuate.endpoint.AbstractEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration for exporting metrics to Prometheus.
 *
 * @author Jon Schneider
 * @author David J. M. Karlsen
 * @author Johnny Lim
 */
@Configuration
@AutoConfigureBefore({CompositeMeterRegistryAutoConfiguration.class,
        SimpleMetricsExportAutoConfiguration.class})
@AutoConfigureAfter(MetricsAutoConfiguration.class)
@ConditionalOnBean(Clock.class)
@ConditionalOnClass(PrometheusMeterRegistry.class)
@ConditionalOnProperty(prefix = "management.metrics.export.prometheus", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PrometheusProperties.class)
@Import(StringToDurationConverter.class)
public class PrometheusMetricsExportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PrometheusConfig prometheusConfig(PrometheusProperties props) {
        return new PrometheusPropertiesConfigAdapter(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public PrometheusMeterRegistry prometheusMeterRegistry(PrometheusConfig config, CollectorRegistry collectorRegistry,
                                                           Clock clock) {
        return new PrometheusMeterRegistry(config, collectorRegistry, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public CollectorRegistry collectorRegistry() {
        return new CollectorRegistry(true);
    }

    @ManagementContextConfiguration
    @ConditionalOnClass(AbstractEndpoint.class)
    public static class PrometheusScrapeEndpointConfiguration {
        @Bean
        public PrometheusScrapeEndpoint prometheusEndpoint(CollectorRegistry collectorRegistry) {
            return new PrometheusScrapeEndpoint(collectorRegistry);
        }

        @Bean
        @ConditionalOnEnabledEndpoint("prometheus")
        public PrometheusScrapeMvcEndpoint prometheusMvcEndpoint(PrometheusScrapeEndpoint delegate) {
            return new PrometheusScrapeMvcEndpoint(delegate);
        }
    }

    /**
     * Configuration for <a href="https://github.com/prometheus/pushgateway">Prometheus
     * Pushgateway</a>.
     */
    @Configuration
    @ConditionalOnClass(PushGateway.class)
    @ConditionalOnProperty(prefix = "management.metrics.export.prometheus.pushgateway", name = "enabled")
    public static class PrometheusPushGatewayConfiguration {

        /**
         * The fallback job name. We use 'spring' since there's a history of Prometheus
         * spring integration defaulting to that name from when Prometheus integration
         * didn't exist in Spring itself.
         */
        private static final String FALLBACK_JOB = "spring";

        @Bean
        @ConditionalOnMissingBean
        public PrometheusPushGatewayManager prometheusPushGatewayManager(
                CollectorRegistry collectorRegistry,
                PrometheusProperties prometheusProperties, Environment environment) {
            PrometheusProperties.Pushgateway properties = prometheusProperties
                    .getPushgateway();
            PushGateway pushGateway = new PushGateway(properties.getBaseUrl());
            Duration pushRate = properties.getPushRate();
            String job = getJob(properties, environment);
            Map<String, String> groupingKey = properties.getGroupingKey();
            PrometheusPushGatewayManager.ShutdownOperation shutdownOperation = properties.getShutdownOperation();
            return new PrometheusPushGatewayManager(pushGateway, collectorRegistry,
                    pushRate, job, groupingKey, shutdownOperation);
        }

        private String getJob(PrometheusProperties.Pushgateway properties,
                Environment environment) {
            String job = properties.getJob();
            job = (job != null) ? job
                    : environment.getProperty("spring.application.name");
            return (job != null) ? job : FALLBACK_JOB;
        }

    }

}
